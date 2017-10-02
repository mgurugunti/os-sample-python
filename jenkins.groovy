#!/usr/bin/groovy

////
// This pipeline requires the following plugins:
// Kubernetes Plugin 0.10
////

String ocpApiServer = env.OCP_API_SERVER ? "${env.OCP_API_SERVER}" : "https://openshift.default.svc.cluster.local"

node('master') {

  env.NAMESPACE = readFile('/var/run/secrets/kubernetes.io/serviceaccount/namespace').trim()
  env.TOKEN = readFile('/var/run/secrets/kubernetes.io/serviceaccount/token').trim()
  env.OC_CMD = "oc --token=${env.TOKEN} --server=${ocpApiServer} --certificate-authority=/run/secrets/kubernetes.io/serviceaccount/ca.crt --namespace=${env.NAMESPACE}"

  env.APP_NAME = "${env.JOB_NAME}".replaceAll(/-?pipeline-?/, '').replaceAll(/-?${env.NAMESPACE}-?/, '')
  def projectBase = "${env.NAMESPACE}".replaceAll(/-dev/, '')
  env.STAGE1 = "${projectBase}"
  env.STAGE2 = "${projectBase}-stage"
  
}

node('maven') {
//  def artifactory = Artifactory.server(env.ARTIFACTORY_SERVER)
  // def artifactoryMaven = Artifactory.newMavenBuild()
  // def buildInfo = Artifactory.newBuildInfo()
  // def scannerHome = tool env.SONARQUBE_TOOL
  def mvnHome = "/usr/share/maven/"
  def mvnCmd = "${mvnHome}bin/mvn"
  String pomFileLocation = env.BUILD_CONTEXT_DIR ? "${env.BUILD_CONTEXT_DIR}/pom.xml" : "pom.xml"

  
  stage('Build Image') {
println "Building Image"  
    openshiftBuild apiURL: "${ocpApiServer}", authToken: "${env.TOKEN}", bldCfg: 'maheshpythontest', buildName: 'maheshpythontest', checkForTriggeredDeployments: 'false', namespace: "${STAGE1}"

  }

  stage("Verify Deployment to ${env.STAGE1}") {

    openshiftVerifyDeployment(deploymentConfig: "${env.APP_NAME}", namespace: "${STAGE1}", verifyReplicaCount: true)

    //input "Promote Application to ${env.STAGE2}?"
  }

  stage("Promote To ${env.STAGE2}") {
    openshiftTag (alias: 'true', apiURL: "${ocpApiServer}", 
                  authToken: "${env.TOKEN}", destStream: "${env.APP_NAME}", 
                  destTag: 'latest', destinationAuthToken: "${env.TOKEN}", destinationNamespace: "${env.STAGE2}", 
                  namespace: "${env.STAGE1}", srcStream: "${env.APP_NAME}", srcTag: 'latest', verbose: 'false')
  }

  stage("Verify Deployment to ${env.STAGE2}") {

    openshiftVerifyDeployment(deploymentConfig: "${env.APP_NAME}", namespace: "${STAGE2}", verifyReplicaCount: true)

    //input "Promote Application to ${env.STAGE3}?"
  }

  
}


println "Application ${env.APP_NAME} is now in Production!"
