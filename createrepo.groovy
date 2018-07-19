@Grab(group='org.codehaus.groovy.modules.http-builder', module='http-builder', version='0.7' )
@Grab(group='org.codehaus.jackson', module='jackson-mapper-asl', version='1.9.0' )

import groovyx.net.http.RESTClient
import static groovyx.net.http.ContentType.*
import org.codehaus.jackson.JsonNode;
import org.codehaus.jackson.map.ObjectMapper;
import java.io.IOException;
import java.io.StringReader;
import javax.xml.ws.Response
import groovy.json.JsonBuilder
import groovy.json.JsonOutput
import groovy.json.JsonSlurper
import hudson.model.*

def environment = System.getenv("Environment")
def orgname = System.getenv("Orgname")
repolist = System.getenv("Reponame")
println environment
println orgname
println repolist

if (environment == "QA") {
    giturl = System.getenv("GHE_QA")
    gittoken = System.getenv("qagittoken")
    continummurl = System.getenv("qa_continummurl")
    continuumtoken = System.getenv("qacontinuumtoken")
}

if (environment == "PROD") {
    giturl = System.getenv("GHE_PROD")
    gittoken = System.getenv("prodgittoken")
    continummurl = System.getenv("prod_continummurl")
    continuumtoken = System.getenv("prodcontinuumtoken")
}

String[] repolist = repolist.split(",");
for(String reponame : repolist)
{
    reponame = reponame.toLowerCase()

    println "Check Repo Exist"
    def url = giturl + "/api/v3/repos/" + orgname + "/" + reponame + "?access_token=" + gittoken
    println url
    def get = new URL(url).openConnection();
    def getRC = get.getResponseCode();
    println(getRC);
    if(getRC.equals(200))
    {
        println "Check codeowner file exist"
        url = giturl + "/api/v3/repos/" + orgname + "/" + reponame +"/contents/.github/CODEOWNERS?access_token=" + gittoken
        println url
        def chekfileexist = new URL(url).openConnection();
        def gerRCResult = chekfileexist.getResponseCode();
        //println(gerRCResult);
        if (gerRCResult.equals(200))
        {
            println "CODEOWNERS file already exists"
            println "Get Teams ID for Code-Owners & Devloper"
            url = giturl + "/api/v3/orgs/" + orgname + "/teams?access_token=" + gittoken
            def getid = new URL(url).openConnection();
            def getidres = getid.getResponseCode();
            def idresp = getid.getInputStream().getText()
            json = new JsonSlurper().parseText(idresp)
            def codeownerNode = json.find { it.name == 'Code-Owners' }
            def codeownerid = codeownerNode.id
            def devNode = json.find { it.name == 'Developers' }
            def devid = devNode.id
            if (getidres == 200) {
                url = giturl + "/api/v3/teams/" + codeownerid + "/repos/" + orgname + "/" + reponame + "?access_token=" + gittoken
                println url
                def codeowner = new RESTClient(url)
                data = ["permission": "push"]

                def codeownerres = codeowner.put(
                        body: data,
                        requestContentType: JSON
                )
                println("Code-Owners team added " + codeownerres.status)

                url = giturl + "/api/v3/teams/" + devid + "/repos/" + orgname + "/" + reponame + "?access_token=" + gittoken
                println url
                def developers = new RESTClient(url)
                data = ["permission": "push"]

                def developersres = developers.put(
                        body: data,
                        requestContentType: JSON
                )
                println("Developers team added " + developersres.status)
            }

            println "Get Branch Name"
            url = giturl + "/api/v3/repos/" + orgname + "/" + reponame + "/branches?access_token=" + gittoken
            def getbranch = new URL(url).openConnection();
            //def getidres = getbranch.getResponseCode();
            def branchresp = getbranch.getInputStream().getText()
            json = new JsonSlurper().parseText(branchresp)
            def brnachnode = json.findAll { it }
            brnachnode = brnachnode.name
            mbranch = (brnachnode.toString().replace("[", "").replace("]", "").replace(" ", ""));
            println mbranch
            //def (mbranch, rbrnach) =  brnachnode.split(',')

            println "Protection for master branch"
            if (mbranch == "master") {
                url = giturl + "/api/v3/repos/" + orgname + "/" + reponame + "/branches/" + mbranch + "/protection?access_token=" + gittoken
                println url
                def masbranch = new RESTClient(url)
                data = ('{\n' +
                        ' "required_pull_request_reviews":{"include_admins":true,"strict":true,"contexts":[],"require_code_owner_reviews":true}\n' +
                        ',"restrictions": null, "enforce_admins": null, "required_status_checks": null}')
                def mastbranchres = masbranch.put(
                        contentType: JSON,
                        body: data,
                        headers: [Accept: 'application/vnd.github.loki-preview+json'])
                println("Master Branch protection applied " + mastbranchres.status)
            }
            /*
            println "Protection for release branch"
             if (rbrnach == "release1")
            {
                  url = giturl + "/api/v3/repos/" + orgname + "/" + reponame +"/branches/" + rbrnach + "/protection?access_token=" + gittoken
                  println url
                  def releasebranch = new RESTClient(url)
                  data = ('{"required_pull_request_reviews":{"include_admins":true,"strict":true,"contexts":[]},"restrictions": null, "enforce_admins": null, "required_status_checks": null}')
                  def releasebranchres = releasebranch.put(
                          contentType: JSON,
                          body: data,
                          headers: [Accept: 'application/vnd.github.loki-preview+json'])
                  println("Developers team added " + releasebranchres.status)
            }
            */
            println "Setup Continuum Webhook"
            url = giturl + "/api/v3/repos/" + orgname + "/" + reponame + "/hooks?access_token=" + gittoken
            RESTClient hook = new RESTClient(url)
            def webhookurl = continummurl + "/api/submit_change?toproject=" + reponame + "-Project-ProxyAPI" + "&token=" + continuumtoken
            data = ('{"config": {"url": ' + '"' + webhookurl + '"' + ',"content_type": "json"},"name": "web", "active": true, "events": ["push"]}')
            println data
            def hookres = hook.post(
                    contentType: JSON,
                    body: data)
            println("Status: " + hookres.status)
        }
        else {
            println "Creating CODEOWNERS file"
            url = giturl + "/api/v3/repos/" + orgname + "/" + reponame + "/contents/.github/CODEOWNERS?access_token=" + gittoken
            def newclient = new RESTClient(url)
            data = ["path"   : "CODEOWNERS",
                    "message": "my commit message",
                    "content": "IyBBbGwgbWVyZ2VzIHRvIG1hc3RlciBicmFuY2ggd2lsbCByZXF1aXJlIGFwcHJvdmFsIGJ5IGEgbWVtYmVyIG9mICJDb2RlLU93bmVycyIgdGVhbQ0KKiBAYXBpcy9Db2RlLU93bmVycw0K"
            ]

            def filecreateresp = newclient.put(
                    body: data,
                    requestContentType: JSON
            )
            println("Status from file create " + filecreateresp.status)

            if (filecreateresp.status == 201) {
                println "Get SHA from master head"
                url = giturl + "/api/v3/repos/" + orgname + "/" + reponame + "/git/refs/heads?access_token=" + gittoken
                def getsha = new URL(url).openConnection();
                def getres = getsha.getResponseCode();
                def sharesp = getsha.getInputStream().getText()
                json = new JsonSlurper().parseText(sharesp)
                shares = json.object.sha.unique()
                strshares = (shares.toString().replace("[", "").replace("]", ""));

                if (getres == 200) {
                    /*

                    println "Creating branch release1"
                    url = giturl + "/api/v3/repos/" + orgname + "/" + reponame +"/git/refs?access_token=" + gittoken
                    def branchclient = new RESTClient(url)
                    data = [
                            "ref": "refs/heads/release1",
                            "sha": strshares
                    ]

                    def branchcreateresp = branchclient.post(
                            body: data,
                            requestContentType: JSON
                    )
                    println("Status from file create " + branchcreateresp.status)
                    */
                }

                println "Get Teams ID for Code-Owners & Devloper"
                url = giturl + "/api/v3/orgs/" + orgname + "/teams?access_token=" + gittoken
                def getid = new URL(url).openConnection();
                def getidres = getid.getResponseCode();
                def idresp = getid.getInputStream().getText()
                json = new JsonSlurper().parseText(idresp)
                def codeownerNode = json.find { it.name == 'Code-Owners' }
                def codeownerid = codeownerNode.id
                def devNode = json.find { it.name == 'Developers' }
                def devid = devNode.id
                if (getidres == 200) {
                    url = giturl + "/api/v3/teams/" + codeownerid + "/repos/" + orgname + "/" + reponame + "?access_token=" + gittoken
                    println url
                    def codeowner = new RESTClient(url)
                    data = ["permission": "push"]

                    def codeownerres = codeowner.put(
                            body: data,
                            requestContentType: JSON
                    )
                    println("Code-Owners team added " + codeownerres.status)

                    url = giturl + "/api/v3/teams/" + devid + "/repos/" + orgname + "/" + reponame + "?access_token=" + gittoken
                    println url
                    def developers = new RESTClient(url)
                    data = ["permission": "push"]

                    def developersres = developers.put(
                            body: data,
                            requestContentType: JSON
                    )
                    println("Developers team added " + developersres.status)
                }

                println "Get Branch Name"
                url = giturl + "/api/v3/repos/" + orgname + "/" + reponame + "/branches?access_token=" + gittoken
                def getbranch = new URL(url).openConnection();
                //def getidres = getbranch.getResponseCode();
                def branchresp = getbranch.getInputStream().getText()
                json = new JsonSlurper().parseText(branchresp)
                def brnachnode = json.findAll { it }
                brnachnode = brnachnode.name
                mbranch = (brnachnode.toString().replace("[", "").replace("]", "").replace(" ", ""));
                println mbranch
                //def (mbranch, rbrnach) =  brnachnode.split(',')

                println "Protection for master branch"
                if (mbranch == "master") {
                    url = giturl + "/api/v3/repos/" + orgname + "/" + reponame + "/branches/" + mbranch + "/protection?access_token=" + gittoken
                    println url
                    def masbranch = new RESTClient(url)
                    data = ('{\n' +
                            ' "required_pull_request_reviews":{"include_admins":true,"strict":true,"contexts":[],"require_code_owner_reviews":true}\n' +
                            ',"restrictions": null, "enforce_admins": null, "required_status_checks": null}')
                    def mastbranchres = masbranch.put(
                            contentType: JSON,
                            body: data,
                            headers: [Accept: 'application/vnd.github.loki-preview+json'])
                    println("Master Branch protection applied " + mastbranchres.status)
                }
                /*
                println "Protection for release branch"
                 if (rbrnach == "release1")
                {
                      url = giturl + "/api/v3/repos/" + orgname + "/" + reponame +"/branches/" + rbrnach + "/protection?access_token=" + gittoken
                      println url
                      def releasebranch = new RESTClient(url)
                      data = ('{"required_pull_request_reviews":{"include_admins":true,"strict":true,"contexts":[]},"restrictions": null, "enforce_admins": null, "required_status_checks": null}')
                      def releasebranchres = releasebranch.put(
                              contentType: JSON,
                              body: data,
                              headers: [Accept: 'application/vnd.github.loki-preview+json'])
                      println("Developers team added " + releasebranchres.status)
                }
                */
                println "Setup Continuum Webhook"
                url = giturl + "/api/v3/repos/" + orgname + "/" + reponame + "/hooks?access_token=" + gittoken
                RESTClient hook = new RESTClient(url)
                def webhookurl = continummurl + "/api/submit_change?toproject=" + reponame + "-Project-ProxyAPI" + "&token=" + continuumtoken
                data = ('{"config": {"url": ' + '"' + webhookurl + '"' + ',"content_type": "json"},"name": "web", "active": true, "events": ["push"]}')
                println data
                def hookres = hook.post(
                        contentType: JSON,
                        body: data)
                println("Status: " + hookres.status)
            }
        }
    }
    else
    {
        println "Creating Repository"
        url = giturl + "/api/v3/orgs/" + orgname + "/repos?access_token=" + gittoken
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

        if (response.status == 201)
        {
            println "Creating CODEOWNERS file"
            url = giturl + "/api/v3/repos/" + orgname + "/" + reponame +"/contents/.github/CODEOWNERS?access_token=" + gittoken
            def newclient = new RESTClient(url)
            data =         ["path": "CODEOWNERS",
                            "message": "my commit message",
                            "content": "IyBBbGwgbWVyZ2VzIHRvIG1hc3RlciBicmFuY2ggd2lsbCByZXF1aXJlIGFwcHJvdmFsIGJ5IGEgbWVtYmVyIG9mICJDb2RlLU93bmVycyIgdGVhbQ0KKiBAYXBpcy9Db2RlLU93bmVycw0K"
            ]

            def filecreateresp = newclient.put(
                    body: data,
                    requestContentType : JSON
            )
            println ("Status from file create " + filecreateresp.status)

            if (filecreateresp.status == 201)
            {
                println "Get SHA from master head"
                url = giturl + "/api/v3/repos/" + orgname + "/" + reponame +"/git/refs/heads?access_token=" + gittoken
                def getsha = new URL(url).openConnection();
                def getres = getsha.getResponseCode();
                def sharesp = getsha.getInputStream().getText()
                json = new JsonSlurper().parseText(sharesp)
                shares = json.object.sha.unique()
                strshares = (shares.toString().replace("[","").replace("]",""));

                if (getres == 200)
                {
                    /*

                    println "Creating branch release1"
                    url = giturl + "/api/v3/repos/" + orgname + "/" + reponame +"/git/refs?access_token=" + gittoken
                    def branchclient = new RESTClient(url)
                    data = [
                            "ref": "refs/heads/release1",
                            "sha": strshares
                    ]

                    def branchcreateresp = branchclient.post(
                            body: data,
                            requestContentType: JSON
                    )
                    println("Status from file create " + branchcreateresp.status)
                    */
                }

                println "Get Teams ID for Code-Owners & Devloper"
                url = giturl + "/api/v3/orgs/" + orgname + "/teams?access_token=" + gittoken
                def getid = new URL(url).openConnection();
                def getidres = getid.getResponseCode();
                def idresp = getid.getInputStream().getText()
                json = new JsonSlurper().parseText( idresp )
                def codeownerNode = json.find { it.name == 'Code-Owners' }
                def codeownerid = codeownerNode.id
                def devNode = json.find { it.name == 'Developers' }
                def devid = devNode.id
                if (getidres == 200)
                {
                    url = giturl + "/api/v3/teams/" + codeownerid + "/repos/" + orgname + "/"  + reponame +"?access_token=" + gittoken
                    println url
                    def codeowner = new RESTClient(url)
                    data = ["permission": "push"]

                    def codeownerres = codeowner.put(
                            body: data,
                            requestContentType : JSON
                    )
                    println("Code-Owners team added " + codeownerres.status)

                    url = giturl + "/api/v3/teams/" + devid + "/repos/" + orgname + "/"  + reponame +"?access_token=" + gittoken
                    println url
                    def developers = new RESTClient(url)
                    data = ["permission": "push"]

                    def developersres = developers.put(
                            body: data,
                            requestContentType : JSON
                    )
                    println("Developers team added " + developersres.status)
                }

                println "Get Branch Name"
                url = giturl + "/api/v3/repos/" + orgname + "/" + reponame +"/branches?access_token=" + gittoken
                def getbranch = new URL(url).openConnection();
                //def getidres = getbranch.getResponseCode();
                def branchresp = getbranch.getInputStream().getText()
                json = new JsonSlurper().parseText( branchresp )
                def brnachnode = json.findAll {it}
                brnachnode = brnachnode.name
                mbranch = (brnachnode.toString().replace("[","").replace("]","").replace(" ",""));
                println mbranch
                //def (mbranch, rbrnach) =  brnachnode.split(',')

                println "Protection for master branch"
                if (mbranch == "master")
                {
                    url = giturl + "/api/v3/repos/" + orgname + "/" + reponame +"/branches/" + mbranch + "/protection?access_token=" + gittoken
                    println url
                    def masbranch = new RESTClient(url)
                    data = ('{\n' +
                            ' "required_pull_request_reviews":{"include_admins":true,"strict":true,"contexts":[],"require_code_owner_reviews":true}\n' +
                            ',"restrictions": null, "enforce_admins": null, "required_status_checks": null}')
                    def mastbranchres = masbranch.put(
                            contentType: JSON,
                            body: data,
                            headers: [Accept: 'application/vnd.github.loki-preview+json'])
                    println("Master Branch protection applied " + mastbranchres.status)
                }
                /*
                println "Protection for release branch"
                 if (rbrnach == "release1")
                {
                      url = giturl + "/api/v3/repos/" + orgname + "/" + reponame +"/branches/" + rbrnach + "/protection?access_token=" + gittoken
                      println url
                      def releasebranch = new RESTClient(url)
                      data = ('{"required_pull_request_reviews":{"include_admins":true,"strict":true,"contexts":[]},"restrictions": null, "enforce_admins": null, "required_status_checks": null}')
                      def releasebranchres = releasebranch.put(
                              contentType: JSON,
                              body: data,
                              headers: [Accept: 'application/vnd.github.loki-preview+json'])
                      println("Developers team added " + releasebranchres.status)
                }
                */
                println "Setup Continuum Webhook"
                url = giturl + "/api/v3/repos/" + orgname + "/"  + reponame +"/hooks?access_token=" + gittoken
                RESTClient hook = new RESTClient(url)
                def webhookurl = continummurl +"/api/submit_change?toproject=" + reponame + "-Project-ProxyAPI" + "&token=" + continuumtoken
                data = ('{"config": {"url": ' + '"' + webhookurl + '"' + ',"content_type": "json"},"name": "web", "active": true, "events": ["push"]}')
                println data
                def hookres = hook.post(
                        contentType: JSON,
                        body: data)
                println("Status: " + hookres.status)
            }
        }
    }
}
