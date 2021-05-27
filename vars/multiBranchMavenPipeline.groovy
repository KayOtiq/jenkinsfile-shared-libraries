// Use this pipeline on multi-branch jenkins pipelines

import Constants

def call (Map config = [:] ) {

    pipeline {
        agent any

        tools {
            // Note: this should match with the tool name configured in your jenkins instance (JENKINS_URL/configureTools/)
            maven "Maven 3.6.3"
            jdk  "Java JDK 8"
        }
        options {
            //Sets the number of build to keep
            buildDiscarder(logRotator(numToKeepStr: '5', artifactNumToKeepStr: '5'))
            //timestamps()
        }
        environment {
            // **********************************
            // ****  USER DEFINED VARIABLES  ****
            // **********************************

            //Build Variables
            VERSION = "${config.version}" 
            ARTIFACT_ID = "${config.artifactId}" 
            GROUP_ID = "${config.groupId}"  
            PACKAGING = "${config.packaging}"  
            NEXUS_IQ_STAGE = "${config.nexusIqStage}"  //default stages are 'build', 'stage-release',  'release',  'operate'   

            //Git Repository Variables
            GIT_URL = "${config.gitUrl}"             

            EMAIL_RESULTS = "${config.emailResultsTo}"  

            //*****************  Resused Vars ****************************

            //SonarQube Variables
            SQ_PROJECT_KEY = "${ARTIFACT_ID}"
            SQ_PROJECT_NAME = "${ARTIFACT_ID}"
            
            PUBLISH_PATH = "${WORKSPACE}\\target\\${ARTIFACT_ID}-${VERSION}-SNAPSHOT.${PACKAGING}"

            //Nexus IQ Policy Evaluation Variables
            NEXUS_IQ_NAME = "${ARTIFACT_ID}"  //IQ Application name; must be added to Nexus IQ prior to pipeline run to complete successfully

        }

        stages {
            stage(" Initialize ") {
                steps {
                    script {
                        log.message("Initialization....")
                        log.separator()
                        log.message(" This pipeline was triggered by the ${BRANCH_NAME} branch ")
                    }
                    deleteDir() //delete the workspace
                    
                }
            }
            stage(" Source ") {
                steps {
                    script {
                        log.message("Retrieving source... ")
                        sharedLibrary.getSourceGit()

                        log.separator()
                    }
                }
            }
            stage(" Info ") {
                steps {
                    script {
                        sharedLibrary.displayInfo()
                        log.separator()
                    }
                }
            }

        
            stage(" Maven Clean & Run Unit Tests") {
                steps {
                    script {
                        bat "mvn clean test -Drevision='${VERSION}-SNAPSHOT'" // --settings settings.xml"
                        log.separator()
                    }
                }
            }
           
            stage(" Maven Sonar Scan ") {
                when{
                    anyOf { branch 'test'; branch 'staging' }
                }
                steps {
                    script {
                        log.message("I only run in the test and staging branches")
                        //sharedLibrary.runSonarScanMaven()
                        withSonarQubeEnv(credentialsId:  Constant.SQ_CREDENTIALS_ID, installationName: Constant.SQ_INSTALLATION_NAME) {

                            "mvn sonar:sonar \
                            -Dsonar.projectKey=${SQ_PROJECT_KEY} \
                            -Dsonar.projectName=${SQ_PROJECT_NAME} \
                            -Dsonar.projectVersion=${VERSION}"
                            
                        }
                        log.separator()
                    }
                }
            }
        
            stage("Nexus IQ Scan") {
                 when{
                    anyOf { branch 'test'; branch 'staging' }
                }
                steps {
                    script{
                        log.message("I only run in the test and staging branches")
                        sharedLibrary.runNexusIQScan()
                        log.separator()
                    }
                }
            }   
            stage(" Maven Package Project") {
                 when{
                    anyOf { branch 'master'; branch 'test'; branch 'staging' }
                }
                steps {
                    script {
                        bat "mvn -Dmaven.test.skip=true package -Drevision='${VERSION}-SNAPSHOT'" // --settings settings.xml"
                        log.separator()
                    }
                }
            }

            stage(" Publish ") {
                when{
                    anyOf { branch 'master'; branch 'test'; branch 'staging' }
                }
                steps {
                    script {
                        log.message("I run when on the test, staging or master branches")
                        sharedLibrary.runNexusPublish()
                        archiveArtifacts "target/*.$PACKAGING"
                        log.separator()
                    }
                }
            }

            stage('Results') {
                steps {
                    script{
                        log.message("I always run")
                        //sharedLibrary.postMavenResults()
                        junit '**/target/surefire-reports/TEST-*.xml'
                        log.separator()
                    }
                }
            }

        } //stages

        post {
            always {
                //publishes the unit test results to Jenkins
                junit "target\\surefire-reports\\*.xml"
            }
            success {
                echo "this runs after each successful build..."
            }
            failure {
                echo "This runs after each failed build ..."
            }
        }

    }
}
