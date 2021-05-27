# Introduction 

This repository is an example of shared libraries used by Jenkins.  

Shared Libraries are useful in keeping pipelines code DRY, by allowing users to share common code and steps across multiple pipelines.  Shared libraries are collections of independent Groovy scripts that can be pulled into a jenkinsfile at runtime.

This library stores the most often used Jenkins pipeline steps to be used at a global level by my Jenkinsfiles, such as SCM Checkouts and Nexus IQ scanning.  There are also has templates for Maven and DotNet builds with unit testing and various scanning mechanisms, such as SonarQube.

There are tons of docs and articles on how to get started and what shared libraries are, but very few working examples.  Once I got mine up and running, I thought it would be good to share it with others.  

*Please note, I have scrubbed my specific info out, so you will need to add your own specific information into src/Constants.groovy*


# Getting Started

## How to reference a shared library in a pipeline
Shared libraries method can store highly used utilities, plug-in steps or entire descriptive or scripted pipelines. This library uses descriptive pipelines.

Shared libraries can only be used in jenkinsfiles. Starting from your descriptive .jenkinsfile, include the following at the top of the .jenkinsfile to instantiate the library:

```
@Library('globalLibrary') _


```
**The "_" is not a typo!**

* 'globalLibrary' is the name used in the Shared Library setup on the Jenkins Controller. 
*  The class or file name is 'sharedLibrary.groovy'. 
*  The 'displayInfo()' method is in the 'sharedLibrary.groovy' file/class


## To call a step from the library from within a .jenkinfile

1)  Input variables can either be defined as part of the pipeline "environment" section, where the variable has the exact same name in the environment as defined in the library:  

```     
environment {
    VERSION = "1.0.1"  
    ARTIFACT_ID = 'JavaSamplePipeline' 
    GROUP_ID = 'io.kotiq.examples' 
    PUBLISH_PATH = "${WORKSPACE}\\${ARTIFACT_ID}-${VERSION}.${PACKAGING}" 
}
     
```
2) This is the library method that will be used:

```
def displayInfo() {
    //displays GAV info in log
    echo "Artifact ID: ${ARTIFACT_ID}"
    echo "Version: ${VERSION}"
    echo "Group ID: ${GROUP_ID}"
}

```

3)  Add the stage into a pipeline

```

stage("Display GAV") {
    steps {
        script{
            sharedLibrary.displayInfo()
        }
    }
}   

```

4) A library method can also use a mapped parameter


```
def downloadNexusArtifact(Map config = [:]) {

    ARTIFACT_ID = "${config.artifactId}"
    GROUP_ID = "${config.groupId}"
    PACKAGING = "${config.packaging}"
    DOWNLOAD_FILE = "${config.downloadFile}"

    NEXUS_URL =  Constant.NEXUS_URL 
    NEXUS_HOST_REPOSITORY = Constant.NEXUS_HOST_REPOSITORY 

    NEXUS_URI = "${NEXUS_URL}service/rest/v1/search/assets/download?repository=${NEXUS_HOST_REPOSITORY}&group=${GROUP_ID}&name=${ARTIFACT_ID}&maven.extension=${PACKAGING}&sort=version"    

    log.message("Downloading Artifact from Nexus...")
    log.message("NexusURI:  ${NEXUS_URI}")
    log.message("Download FilePath: ${DOWNLOAD_FILE}")
    powershell """Invoke-WebRequest '${NEXUS_URI}' -Method Get -OutFile (New-Item -Path '${DOWNLOAD_FILE}' -Force ) """

    }

```

5)  Add the stage into a pipeline

```

stage("Download Artifact from Nexus Repo") {
    steps {
        script{
            sharedLibrary.downloadNexusArtifact(
                artifactId: 'JavaSamplePipeline',
                groupId: 'io.kotiq.examples',
                version: "1.0.1",
                packaging:  'jar',
            )
        }
    }
}   

```

## Jenkinsfiles as Configuration

If you have multiple pipelines with the same steps, you can put the entire pipeline into a method, turning the jenkinsfile into more of a configuration

```
@Library("globalLibrary@develop") _

defaultMavenPipeline(
    artifactId: 'JavaSamplePipeline',
    groupId: 'io.kotiq.examples',
    version: utils.getPomVersion(),
    gitUrl: Constant.GIT_URL,
    packaging:  'jar',
    nexusIqStage: "build",
    emailResultsTo: "lynda@kotiq.io" //send me a copy of the email results

)

```

# More Info

If you would like more information on Shared Libraries, refer to 
[Extending with Shared Libraries](https://www.jenkins.io/doc/book/pipeline/shared-libraries/)

# Contribute
If you would like to contribute or learn more about to the Shared Libraries, please contact me at

If you want to learn more about creating good readme files then refer the following [guidelines](https://www.visualstudio.com/en-us/docs/git/create-a-readme). You can also seek inspiration from the below readme files:
- [ASP.NET Core](https://github.com/aspnet/Home)
- [Visual Studio Code](https://github.com/Microsoft/vscode)
- [Chakra Core](https://github.com/Microsoft/ChakraCore)