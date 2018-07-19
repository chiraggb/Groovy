@Grab(group='org.codehaus.groovy.modules.http-builder', module='http-builder', version='0.7' )
@Grab(group='org.codehaus.jackson', module='jackson-mapper-asl', version='1.9.0' )

import groovyx.net.http.ContentType
import groovyx.net.http.HTTPBuilder
import groovyx.net.http.RESTClient
import static groovyx.net.http.ContentType.*
import org.apache.http.conn.HttpHostConnectException
import org.apache.http.client.HttpResponseException
import org.apache.http.HttpStatus
import org.apache.http.HttpRequestInterceptor
import org.apache.http.protocol.HttpContext
import org.apache.http.HttpRequest
import hudson.model.*


def environment = System.getenv("environment")
def orgname = System.getenv("orgname")
def repolist = System.getenv("reponame")
println environment
println orgname
println reponame

//def environment = System.getenv("environment")
//def orgname = "DevOPS"
//def repolist = ["Test1", "TEST2", "TEST3"]
//println environment


    if (environment == "QA") {
        giturl = System.getenv("GHE_QA")
        gittoken = System.getenv("qatoken")
    }
    println giturl
    println gittoken 


    //giturl = "https://github-qa.suntrust.com"
    //gittoken = "f9d66a2760a20c6c020ba22d974027c397ede370"
//def list = ["A", "B", "C"]
for (reponame in repolist) {
    reponame = reponame.toLowerCase()
    println reponame
    println "Creating Repository"
    def url = giturl + "/api/v3/orgs/" + orgname + "/repos?access_token=" + gittoken
    println url
    def client = new RESTClient(url)
    data =         ["name": reponame,
                    "description": "Creating Repository for " + reponame,
                    "private": false,
                    "auto_init": true,
                    "gitignore_template": "Java"
    ]

    def response = client.post(
            body: data,
            requestContentType : JSON
    )

    println("Status: " + response.status)
}





