@Grab('org.mongodb:mongodb-driver:3.2.2')
@Grab(group='org.codehaus.groovy.modules.http-builder', module='http-builder', version='0.7' )
@Grab(group='org.codehaus.jackson', module='jackson-mapper-asl', version='1.9.0' )

import com.mongodb.MongoClient;
import com.mongodb.MongoException;
import com.mongodb.WriteConcern;
import com.mongodb.DB;
import com.mongodb.DBCollection;
import com.mongodb.BasicDBObject;
import com.mongodb.DBObject;
import com.mongodb.DBCursor;
import com.mongodb.ServerAddress;
import groovyx.net.http.RESTClient
import groovyx.net.http.*
import org.codehaus.jackson.JsonNode;
import org.codehaus.jackson.map.ObjectMapper;
import java.io.IOException;
import java.io.StringReader;
import javax.xml.ws.Response
import static groovyx.net.http.ContentType.*
import static groovyx.net.http.Method.*
import groovy.json.JsonBuilder
import groovy.json.JsonOutput
import groovyx.net.http.RESTClient
import static groovyx.net.http.ContentType.JSON
import groovy.json.JsonSlurper

/*
def exportapiname = 'scenariomaintenance'
def importapiname = 'pwmloadparent'
def qaexporturi =  System.getenv("qa_continummurl")//'http://10.7.152.114:8080'
def prodexporturi = System.getenv("prod_continummurl")
def qaexporttoken = System.getenv("qaexporttoken")
def prodexporttoken = System.getenv("prodexporttoken")
def qaexportmongoclient = System.getenv("qaexportmongoclient")
def prodexportmongoclient = System.getenv("prodexportmongoclient")
def qaimporturi =  System.getenv("qa_continummurl")//'http://10.7.152.114:8080'
def prodinporrturi = System.getenv("prod_continummurl")
def qaimporttoken = System.getenv("qaexporttoken")
def prodimporttoken = System.getenv("prodexporttoken")*/
def Environment = System.getenv("Environment")
def exportpath =  "/tmp/"

// Setting Variables based on environment selection
if (Environment == "QA") {
    exporturi = System.getenv("qa_continummurl")
    exporttoken = System.getenv("qaexporttoken")
    exportapiname = System.getenv("APITemplate")
    importapinamelist = System.getenv("Reponame").toLowerCase()
    exportmongoclient = System.getenv("qaexportmongoclient")
    importuri  = System.getenv("qa_continummurl")
    importtoken  = System.getenv("qaexporttoken")
     email = System.getenv("Email")
}
if (Environment == "PROD") {
    exporturi = System.getenv("prod_continummurl")
    exporttoken = System.getenv("prodexporttoken")
    exportapiname = System.getenv("APITemplate")
    importapinamelist = System.getenv("Reponame").toLowerCase()
    exportmongoclient = System.getenv("prodexportmongoclient")
    importuri  = System.getenv("prod_continummurl")
    importtoken  = System.getenv("prodexporttoken")
    email = System.getenv("Email")
}

String[] importapinamelist = importapinamelist.split(",");
for(String importapiname : importapinamelist)
{
    MongoClient mongoClient = new MongoClient( exportmongoclient , 27017 );
    DB db = mongoClient.getDB( "continuum" );
    DBCollection coll = db.getCollection("legato.projects");
    BasicDBObject query = new BasicDBObject("name", exportapiname + "-Project-ProxyAPI" );
    BasicDBObject fields = new BasicDBObject();
    fields.put("_id", 0);
    fields.put("name", 1);
    cursor = coll.find(query,fields)
    if(cursor.count() == 1)
    {
        BasicDBObject dbObject = cursor.next();
        def projname = dbObject.get("name");
        println "Exporting Project: " + projname
        // REST API CALL
        url = exporturi + "/api/export_project?token=" + exporttoken + "&project=" + projname
        println url
        def get = new URL(url).openConnection();
        def getRC = get.getResponseCode();
        println(getRC);
        // Export REST API output to JSON file
        if(getRC.equals(200)) {
            def projresp = get.getInputStream().getText()
            projrootNode = new ObjectMapper().readTree(new StringReader(projresp))
            JsonNode projresponse = projrootNode.get("Response")
            println projresponse
            //Change notification email address
            File file = new File(exportpath + "Proj_" + importapiname + "-Project-ProxyAPI" +".json")
            file.text = (projresponse);
            def slurped = new JsonSlurper().parseText(file.text)
            def builder = new JsonBuilder(slurped)
            builder.content.notifications.email.addresses = email
            println(builder.toPrettyString())
            file.text = (builder);
        }
        // Replace project name in file
        file =  new File(exportpath + "Proj_" + importapiname + "-Project-ProxyAPI" +".json")
        def newConfig = file.text.replace(exportapiname, importapiname)
        file.text = newConfig

        //Import project into dest environment
        //========================================
        url = importuri + "/api/import_project?token=" + importtoken
        println url
        def client = new RESTClient(url)
        def jsonSlurper = new JsonSlurper()
        def reader = new BufferedReader(new InputStreamReader(new FileInputStream(file),"UTF-8"));
        data = jsonSlurper.parse(reader);
        println data
        def impresponse = client.post(
                contentType: JSON,
                body: [backup: data],
                headers: [Accept: 'application/json']
                )

        println("Status: " + impresponse.status)
        if (impresponse.status.equals(200))
        {
            println(importapiname + "-Project-ProxyAPI has been imported successfully.")
        }

        // Query for Packages
        BasicDBObject packquery = new BasicDBObject("name", exportapiname + "-Project-ProxyAPI" );
        BasicDBObject packfields = new BasicDBObject();
        packfields.put("_id", 0);
        packfields.put("source.directives.details.package_name", 1);
        cursor = coll.find(packquery,packfields)
        //println cursor[0]
        while(cursor.hasNext())
        {
            BasicDBObject packdbObject = cursor.next();
            packdbObject.toMap()
            def packname = packdbObject.toMap()['source'].toMap()['directives'].inject([], { accum, detail ->
                packageName = detail.toMap()['details'].toMap()['package_name']
                if(packageName != null) {
                    accum.add(packageName)
                }
                return accum
            })
            packname.forEach { packagename ->
                println "package Name " + packagename
                
                // Check if package exist before exporting
                MongoClient mongoCli = new MongoClient( exportmongoclient , 27017 );
                DB pdb = mongoCli.getDB( "continuum" );
                DBCollection pcoll = pdb.getCollection("legato.packages");
                BasicDBObject pquery = new BasicDBObject("name", packagename);
                BasicDBObject pfields = new BasicDBObject();
                pfields.put("name", 0);
                pcursor = pcoll.find(pquery,pfields)
                if(pcursor.count() == 1)
                {
                    url = exporturi + "/api/export_package?token=" + exporttoken + "&package=" + packagename
                    def packget = new URL(url).openConnection();
                    def packgetRC = packget.getResponseCode();
                    // Export REST API output to JSON file
                    if(packgetRC.equals(200)) {
                        packfile = packagename.replace(exportapiname, importapiname)
                        def res = packget.getInputStream().getText()
                        rootNode = new ObjectMapper().readTree(new StringReader(res))
                        JsonNode response = rootNode.get("Response")
                        println response

                        File file = new File(exportpath + "Pack_" + packfile + ".json")
                        //file.text = (packget.getInputStream().getText());
                        file.text = (response);


                    }
                    // Replace package name in file
                    file =  new File(exportpath + "Pack_" + packfile +".json")
                    def packnewConfig = file.text.replace(exportapiname, importapiname)
                    file.text = packnewConfig

                    //Import Package into dest environment
                    //========================================
                    url = importuri + "/api/import_package?token=" + importtoken
                    println url
                    def clientimport = new RESTClient(url)
                    def pakcimpjsonSlurper = new JsonSlurper()
                    def impreader = new BufferedReader(new InputStreamReader(new FileInputStream(file),"UTF-8"));
                    data = pakcimpjsonSlurper.parse(impreader);
                    println data
                    def packimpresponse = clientimport.post(
                            contentType: JSON,
                            body: [backup: data],
                            headers: [Accept: 'application/json']
                    )

                    println("Status: " + packimpresponse.status)
                    if (packimpresponse.status.equals(200))
                    {
                        println("Package has been imported successfully.")
                    }
                }
            }
        }
    }
    else
    {
        println "No project found with that name"
    }
}
