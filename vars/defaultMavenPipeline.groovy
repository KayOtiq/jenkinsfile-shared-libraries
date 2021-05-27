// Use this pipeline for single branch pipelines.

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
        parameters {

            gitParameter name: 'sourceBranch', branchFilter: 'origin/(.*)', defaultValue: 'develop', description: '',  quickFilterEnabled: false, selectedValue: 'DEFAULT', sortMode: 'NONE', tagFilter: '*', type: 'PT_BRANCH'
            choice name: 'deploymentTarget', choices: ['int-dev', 'b2c-dev'], description: 'b2c-dev, int-dev (required).'

      
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
                        echo "Branch Name: ${params.sourceBranch}"
                        echo "Deployment Target: ${params.deploymentTarget}"
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

                steps {
                    script {
                        //sharedLibrary.runSonarScanMaven()
                        withSonarQubeEnv(credentialsId: Constant.SQ_CREDENTIALS_ID, installationName: Constant.SQ_INSTALLATION_NAME) {

                           bat "mvn sonar:sonar \
                            -Dsonar.projectKey=${SQ_PROJECT_KEY} \
                            -Dsonar.projectName=${SQ_PROJECT_NAME} \
                            -Dsonar.projectVersion=${VERSION}"
                            
                        }
                        log.separator()
                    }
                }
            }
        
            stage("Nexus IQ Scan") {

                steps {
                    script{
                        sharedLibrary.runNexusIQScan()
                        log.separator()
                    }
                }
            }   
            stage(" Maven Package Project") {

                steps {
                    script {
                        bat "mvn -Dmaven.test.skip=true package -Drevision='${VERSION}-SNAPSHOT'" // --settings settings.xml"
                        log.separator()
                    }
                }
            }

            stage(" Publish ") {

                steps {
                    script {
                        sharedLibrary.runNexusPublish()
                        archiveArtifacts "target/*.$PACKAGING"
                        log.separator()
                    }
                }
            }

            stage('Results') {
                steps {
                    script{
                        sharedLibrary.postMavenResults()
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
