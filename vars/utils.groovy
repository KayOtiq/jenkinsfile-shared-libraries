def zipSourceDir(Map config = [:]) 
{
    //Use to zip contents in the source path into zip file
    SourcePath = "${config.sourcePath}"
    ZipFile = "${config.zipFileName}"

    log.message(" Scanning Files... ")
    powershell  returnStatus: true, script: """Get-ChildItem -Path '${SourcePath}' | Format-Table  -Property Name,LastWriteTime"""
    powershell  returnStatus: true, script: """Get-Acl '${SourcePath}' | Format-Table  -Property PSChildName,Owner"""
    log.message(" Creating Zip... ")
    log.separator()
    powershell  returnStatus: true, script: """Compress-Archive -path '${SourcePath}' '${ZipFile}' """
}

def getPomVersion(){
    ver = readMavenPom(file: 'pom.xml').getVersion()
}