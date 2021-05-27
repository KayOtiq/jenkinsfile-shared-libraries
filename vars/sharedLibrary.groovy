/* groovylint-disable LineLength, NoDef */
import Constants

def getSourceGit() {
    echo 'Retrieving source... '

    checkout changelog: false, poll: false,
            scm: [$class: 'GitSCM', branches: [[name: "${GIT_BRANCH}"]],
            doGenerateSubmoduleConfigurations: false, extensions: [], gitTool: 'Default', submoduleCfg: [],
            userRemoteConfigs: [[credentialsId: Constant.GIT_CREDENTIALS, url: "${GIT_URL}"]]]
}


def getSourceTFVC() {
    echo 'Retrieving source... '
    checkout changelog: false, poll: false, scm: [$class: 'TeamFoundationServerScm',
                credentialsConfigurer: [$class: 'AutomaticCredentialsConfigurer'],
                projectPath: "${PROJECT_PATH}",
                serverUrl: Constant.AZURE_SERVER_URL, useOverwrite: true, useUpdate: true,
                workspaceName: 'Hudson-${JOB_NAME}-${NODE_NAME}']
}

def nugetRestore() {
    bat '''D:\\Apps\\nuget.exe restore "${WORKSPACE}\\${SOLUTION_NAME}"'''
}

def displayInfo() {
    //displays GAV info in log
    echo "artifact id: ${ARTIFACT_ID}"
    echo "version: ${VERSION}"
}

def runNexusPublish() {
    //publishes to nexus repo using environment variables

    //PUBLISH_PATH
    //VERSION

    echo 'Publishing to Nexus Repository... '
    nexusPublisher nexusInstanceId: Constant.NEXUS_INSTANCE, nexusRepositoryId: Constant.NEXUS_HOST_REPOSITORY,
                    packages: [
                        [$class: 'MavenPackage', mavenAssetList: [
                                [classifier: '', extension: '',
                                filePath: "${PUBLISH_PATH}"]
                            ],
                            mavenCoordinate: [artifactId: "$ARTIFACT_ID", groupId: "$GROUP_ID",
                            packaging: "$PACKAGING", version: "${VERSION}"]
                        ]
                    ]
} 
def runNexusIQScan() { 

    //NEXUS_IQ_NAME
    //NEXUS_IQ_STAGE

    nexusPolicyEvaluation advancedProperties: '', failBuildOnNetworkError: false,
    iqApplication: selectedApplication("${NEXUS_IQ_NAME}"),
    iqStage: "${NEXUS_IQ_STAGE}", jobCredentialsId: ''
}
def runMavenCleanTest() {
    bat "mvn clean test -Drevision='${VERSION}-SNAPSHOT'"
}

def runMavenPackageProject() {
    bat "mvn -Dmaven.test.skip=true package -Drevision='${VERSION}-SNAPSHOT'"
}

def runSonarScanMavenOld() {
    bat "mvn sonar:sonar \
        -Dsonar.projectKey=${SQ_PROJECT_KEY} \
        -Dsonar.host.url=${SQ_HOST_URL} \
        -Dsonar.login=${SQ_TOKEN} \
        -Dsonar.projectName=${SQ_PROJECT_NAME} \
        -Dsonar.projectVersion=${VERSION}"
}

def runSonarScanMaven() {

    //SQ_PROJECT_KEY
    //SQ_PROJECT_NAME
    //VERSION

    withSonarQubeEnv(credentialsId: Constant.SQ_CREDENTIALS_ID, installationName: Constant.SQ_INSTALLATION_NAME) {

    bat "mvn sonar:sonar \
        -Dsonar.projectKey=${SQ_PROJECT_KEY} \
        -Dsonar.projectName=${SQ_PROJECT_NAME} \
        -Dsonar.projectVersion=${VERSION}"
    }
}

def runSonarScanMSBuild() {
    withSonarQubeEnv(credentialsId: Constant.SQ_CREDENTIALS_ID, installationName: Constant.SQ_INSTALLATION_NAME) {
        echo 'SonarScan Begin....'
        bat """${SCANNER_HOME}\\SonarScanner.MSBuild.exe begin /k:${SQ_PROJECT_KEY} /n:${SQ_PROJECT_NAME} /v:${VERSION} /d:sonar.cs.vstest.reportsPaths="${WORKSPACE}\\TestResults\\**.trx" """

        echo 'MSBuild - .sln ....'
        bat """msbuild "${SOLUTION_NAME}" /m  /t:build  /p:DeployOnBuild=true /p:WebPublishMethod=Package /p:PackageAsSingleFile=true /p:CmdLineInMemoryStorage=True /p:SkipInvalidConfigurations=true"""

        echo 'SonarScan End....'
        bat "${SCANNER_HOME}\\SonarScanner.MSBuild.exe end"
    }
}

def runSonarScanMSBuildUnitTest() {

    //SCANNER_HOME
    //SQ_PROJECT_KEY
    //SQ_PROJECT_NAME
    //VERSION
    //SOLUTION_NAME
    //VS_TOOLS

    withSonarQubeEnv(credentialsId: Constant.SQ_CREDENTIALS_ID, installationName: Constant.SQ_INSTALLATION_NAME) {
        echo 'SonarScan Begin ....'
        bat """${SCANNER_HOME}\\SonarScanner.MSBuild.exe begin /k:${SQ_PROJECT_KEY} /n:${SQ_PROJECT_NAME} /v:${VERSION} /d:sonar.cs.vstest.reportsPaths="${WORKSPACE}\\TestResults\\**.trx" """

        echo 'MSBuild - .sln ...'
        bat """msbuild "${SOLUTION_NAME}" /m  /t:build  /p:DeployOnBuild=true /p:WebPublishMethod=Package /p:PackageAsSingleFile=true /p:CmdLineInMemoryStorage=True /p:SkipInvalidConfigurations=true"""

        echo 'Run Unit Test ....'
        bat """"${VS_TOOLS}\\Common7\\IDE\\CommonExtensions\\Microsoft\\TestWindow\\vstest.console.exe" ${TEST_REPORT_PATH} /EnableCodeCoverage /Logger:trx """

        echo 'SonarScan End....'
        bat "${SCANNER_HOME}\\SonarScanner.MSBuild.exe end"
    }
}
def runVSUnitTestCodeCoverage() {

    //VS_TOOLS
    //TEST_REPORT_PATH

    echo 'Run Unit Test and Generate Report....'
    bat """"${VS_TOOLS}\\Common7\\IDE\\CommonExtensions\\Microsoft\\TestWindow\\vstest.console.exe" ${TEST_REPORT_PATH} /EnableCodeCoverage /Logger:trx """

    powershell  '''$env:VSTOOLS = "${VS_TOOLS}"

    $result = Get-ChildItem -Path .\\TestResults -Recurse -Include *.coverage
    & "$($env:VSTOOLS)\\Team Tools\\Dynamic Code Coverage Tools\\CodeCoverage.exe" "analyze" "/output:""$($env:WORKSPACE)\\TestResults\\vstest.coveragexml""" "$($result[0].FullName)"
    '''
}

def postMavenResults() {
    junit '**/target/surefire-reports/TEST-*.xml'
    archiveArtifacts "target/*.$PACKAGING"
// perform workspace cleanup only if the build have passed
// if the build has failed, the workspace will be kept
//cleanWs cleanWhenFailure: false
}
def email() {

    //EMAIL_RECIPIENTS
    
    mail to: "${EMAIL_RECIPIENTS}",
        from: 'jenkins@aaa-calif.com',
        subject: "[Jenkins] ${currentBuild.fullDisplayName} - ${currentBuild.currentResult}",
        body: "Job: \"${env.JOB_NAME}\" build: ${env.BUILD_NUMBER} status: ${currentBuild.currentResult}\n\n\n" +
                "View the build at:\n${env.BUILD_URL}\n\n" +
                "Blue Ocean:\n${env.RUN_DISPLAY_URL}"
}

def downloadNexusArtifact() {

    //ARTIFACT_ID = "${config.artifactId}"
    //GROUP_ID = "${config.groupId}"
    //PACKAGING = "${config.packaging}"
    //DOWNLOAD_FILE = "${config.downloadFile}"

    NEXUS_URL =  Constant.NEXUS_URL 
    NEXUS_HOST_REPOSITORY = Constant.NEXUS_HOST_REPOSITORY 

    NEXUS_URI = "${NEXUS_URL}service/rest/v1/search/assets/download?repository=${NEXUS_HOST_REPOSITORY}&group=${GROUP_ID}&name=${ARTIFACT_ID}&maven.extension=${PACKAGING}&sort=version"    

    log.message("Downloading Artifact from Nexus...")
    log.message("NexusURI:  ${NEXUS_URI}")
    log.message("Download FilePath: ${DOWNLOAD_FILE}")
    powershell """Invoke-WebRequest '${NEXUS_URI}' -Method Get -OutFile (New-Item -Path '${DOWNLOAD_FILE}' -Force ) """

    }

def getPomVersion(){
    ver = readMavenPom(file: 'pom.xml').getVersion()
}
return this
