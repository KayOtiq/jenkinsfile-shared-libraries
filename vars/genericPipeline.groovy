#!groovy
@Library("globalLibrary") _

"""
NAME: Generic Pipeline

DESCRIPTION:
- Used to build, test and deploy the specified application and branch
- Most inputs that are usually manually entered at runtime are retrieved from the pom.xml (artifact id, version).
- This is part of a web or service hook, and triggered on a git push
- Target environment is an input parameter from either the web hook or based on the branch the change is pushed to
"""

def call (Map config = [:]) {

    pipeline {
        agent any
        
        tools {
            // Install the Maven version configured as "Maven 3.6.3" and add it to the path.
            maven "Maven 3.6.3"
            // Install the Jave version configured as "Java JDK 8" and add it to the path.
            jdk  "Java JDK 8"
        }

        environment {

            gitURLprefix = ''
            artifactId = readMavenPom(file: 'pom.xml').getArtifactId() 

            SQ_PROJECT_KEY = "${artifactId}"
            SQ_PROJECT_NAME = "${artifactId}"

        }

        stages {
            stage('Setup Workspace'){
                steps {

                    deleteDir() 
                    log.separator()
                }
            }

            stage('Checkout Source') {
                steps {
                    script {
                        log.message( "checkout changelog")
                        log.message( "Branch: */${params.sourceBranch} ")
                        log.message( "Repo: https://${gitURLprefix}/${artifactId}")
                        log.message( "Artifact ID: ${artifactId}")

                        git branch: "${GIT_BRANCH}", credentialsId: Constant.GIT_CREDENTIALS, url: "https://${gitURLprefix}/${artifactId}"
                        
                        log.separator()
                    }
            }
            }

            stage('Code Check') {
                steps {
                    bat "mvn clean package -Pcode-check -DskipTests --settings settings.xml"
                    script{ log.separator() }
                }
            }

            stage('Increment version') {
                steps {
                    script {
                        bat "git checkout -B ${GIT_BRANCH}"
                        bat "git config user.name 'auto_builder'"
                        bat "git config user.email 'auto_builder@kotiq.io'"
                        bat 'mvn build-helper:parse-version versions:set -DnewVersion=${parsedVersion.majorVersion}.${parsedVersion.minorVersion}.${parsedVersion.nextIncrementalVersion} versions:commit --settings developer-settings.xml'
                        bat "git status"
                        log.separator()
                    }
                    
                }
            }

            stage('Push Changes') {
                environment { 
                    GIT_AUTH = Constant.GIT_CREDENTIALS
                    projectVersion = getPomVersion() 

                }
                steps {
                    script {

                        bat (label: "add", returnStdout: true, script: "git add .")
                        bat (label: "commit", returnStatus: true, script: "git commit --all -m \"Update version to ${projectVersion}.\"")
                        bat (label: "push ${GIT_BRANCH} to remote origin", returnStdout: true, script: "git push --set-upstream https://%GIT_AUTH_USR%:%GIT_AUTH_PSW%@${gitURLprefix}/${artifactId} ${GIT_BRANCH}")
                        log.separator()
                    }
            
                }
            }

            stage('Clean and Unit Test') {
                        steps {
                            bat "mvn clean install "
                            script{log.separator()}
                        }
                    }

            

            stage(" Maven Sonar Scan ") {
                environment {
                    VERSION = getPomVersion() 
                }
                steps {
                    script {
                        withSonarQubeEnv(credentialsId:  Constant.SQ_CREDENTIALS_ID, installationName: Constant.SQ_INSTALLATION_NAME) {
                            "mvn sonar:sonar \
                            -Dsonar.projectKey=${SQ_PROJECT_KEY} \
                            -Dsonar.projectName=${SQ_PROJECT_NAME} \
                            -Dsonar.projectVersion=${VERSION}"
                            log.separator()
                        }
                    }
                }
            }

            stage('Build and Publish ') {
                 when {
                    environment ignoreCase: true, name: 'PRODUCTION', value: 'false'
                }
                steps {
                    bat "mvn clean install deploy "
                    script {log.separator()}
                }
            }


            stage('Publish for Production') {
                when {
                    environment ignoreCase: true, name: 'PRODUCTION', value: 'true'
                }
                steps {
                    bat "mvn mule:deploy  -Dartifact=dummy -Prtf -P${config.deploymentTarget} "
                    script {log.separator()}
                }
            }


        }
    }
}