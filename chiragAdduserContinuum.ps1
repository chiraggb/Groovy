Add-PSSnapin Microsoft.Exchange.Management.PowerShell.SnapIn;
get-pssnapin –registered
$adgroups = [ xml ](Get-Content adgrouplist.xml)
write-host $adgroups
Foreach ($adgroup in $adgroups.groups.group.name)
{
    if ($($ENV:Environment -eq "InnovationLab"))
    {
        $uri=$($ENV:continuum_innovationlab)
        write-host $uri
        $token=$ENV:continuumIL_token
    }
    elseif ($($ENV:Environment -eq "QA"))
    {
        $uri=$($ENV:continuum_qa)
        write-host $uri
        $token=$ENV:continuumQA_token
    }
    elseif ($($ENV:Environment -eq "Prod"))
    {
        $uri=$($ENV:continuum_prod)
        write-host $uri
        $token=$ENV:continuumPROD_token
    }
    $adgroup= $adgroup        
    import-module activedirectory
    $SrvPassword = ConvertTo-SecureString "$($ENV:SrvPassword)" -AsPlainText -Force
    $Credential = New-Object System.Management.Automation.PSCredential ("$ENV:SrvUser", $SrvPassword)
    if ($adgroup -like 'RL.*')
    {
    $Arrayofmembers = Get-ADGroupMember -Credential $Credential -Server "Domain Controller Name here" -identity $adgroup -recursive | Get-ADUser -Property UserPrincipalName| select UserPrincipalName,name,samaccountname
    foreach ($Member in $Arrayofmembers) 
        {                                 
            $body = (ConvertTo-Json  @{
                                        'name' = $adgroup
                                     })                                
            $groupuri = $uri + "api/create_tag?token=" + $token 
            write-host $groupuri
            $group = Invoke-RestMethod -Uri $groupuri -ContentType "application/json" -Method Post -Body $body          
                  
            $mongoDbDriverPath = "C:\mongodriver"
            $dbName = "continuum"
            $collectionName = "users"            
            Add-Type -Path "$($mongoDbDriverPath)\MongoDB.Bson.dll"
            Add-Type -Path "$($mongoDbDriverPath)\MongoDB.Driver.dll"
            $conti1 = $uri.Split("//")[2]
            $contidb = $conti1.Split(":")[0]
            
            $db = [MongoDB.Driver.MongoDatabase]::Create("mongodb://" + $contidb + ":27017/$dbName")
            $collection = $db[$collectionName]
            $query = [MongoDB.Driver.Builders.Query]::EQ('username',"corp\" + $Member.samaccountname.ToLower() )
            $results = $collection.find($query)

            foreach ($result in $results) 
            {
                $uid = $result[“username”]                           
            }
            if ($uid -ne $null)
            {
                $body = (ConvertTo-Json @{                                        
                                        'user' = "corp\" + $Member.samaccountname.ToLower()                                        
                                        'groups' = $adgroup})            
                $requestURI = $uri + "api/update_user?token=" + $token      
                $response = Invoke-RestMethod -Uri $requestURI -ContentType "application/json" -Method Post -Body $body    
                $uid = $null                                                   
            }
            else
            {   
                if($adgroup -eq "RL.APP.Admin" -Or $adgroup -eq "RL.APP.Jenkin.admin")
                {
                    $role = "Developer"
                }                     
                else
                {
                    $role = "User"
                }                  
                $body = (ConvertTo-Json @{
                                    'name' = $Member.name
                                    'user' = "corp\" + $Member.samaccountname.ToLower()
                                    'email' = $Member.UserPrincipalName
                                    'role' = $role
                                    'status' = "enabled"
                                    'authtype' = "ldap"
                                    'groups' = $adgroup})                                                  
                $requestURI = $uri + "api/create_user?token=" + $token    
                #write-host  $requestURI
                $response = Invoke-RestMethod -Uri $requestURI -ContentType "application/json" -Method Post -Body $body 
                write-host "Response $response"
                if ($response -eq "@{ErrorCode=; ErrorDetail=; ErrorMessage=; Method=create_user; Response=}")
                {
                    $name = $Member.name
                    $email = $Member.UserPrincipalName
                    $username = "corp\" + $Member.samaccountname.ToLower()
                    # Send email to user
                    $From = "donotreplycontinuum@xyz.com"
                    $To = $email                           
                    $Subject = "Continuum login information"
                    $Body = "<b><font color=blue>Teammate,</b></font> <br>"
                    $Body += "<br>"
                    $Body += "Version 1 Continuum is the product that has been selected to manage the main orchestrations for all DevOps CI/CD builds and deployments. <br>"
                    $Body += "<br>"
                    $Body += "During certain phases of the process, an approval may be required for deployments into certain environments and after receiving an email of such required action, you will be required to log into Continuum to acknowledge and approve such deployments; therefore, we have created the login credentials for you that is required. <br>"
                    $Body += "<br>"
                    $Body += "If you need additional information, please reach out to your groups' DevOps leader for procedures related to your specific groups' DevOps processes. <br>"  
                    $Body += "<br>"
                    $Body += "<b><font color=blue>Please use the following login information for accessing Version 1 Continuum.</b></font> <br>"
                    $Body += "Username:   $username <br>"
                    $Body += "Password:   Your Active Directory domain account password.<br>"
                    $Body += "Login URL:  $uri <br>"
                    $Body += "<br>"
                    $Body += "Thank You <br>"
                    $Body += "DevOps Team <br>"
                    $SMTPServer = "Smtp Servername"
                    $SMTPPort = "25"
                    write-host $Body
                    Send-MailMessage -From $From -to $To -Subject $Subject -Body $Body -BodyAsHtml -SmtpServer $SMTPServer -port $SMTPPort
                }
            }        
        }
    }
    elseif ($adgroup -like 'DL.*')
    {
        invoke-command -scriptblock {Add-PSSnapin Microsoft.Exchange.Management.PowerShell.SnapIn}
        $Arrayofmembers =  Get-DistributionGroupMember -DomainController "domain controller name here" -Credential $Credential -Identity $adgroup | select PrimarySmtpAddress,name,samaccountname
        foreach ($Member in $Arrayofmembers) 
            {                                 
                $body = (ConvertTo-Json  @{
                                            'name' = $adgroup
                                         })                                
                $groupuri = $uri + "api/create_tag?token=" + $token 
                $group = Invoke-RestMethod -Uri $groupuri -ContentType "application/json" -Method Post -Body $body          
                $mongoDbDriverPath = "C:\mongodriver"
                $dbName = "continuum"
                $collectionName = "users"            
                Add-Type -Path "$($mongoDbDriverPath)\MongoDB.Bson.dll"
                Add-Type -Path "$($mongoDbDriverPath)\MongoDB.Driver.dll"
                $conti1 = $uri.Split("//")[2]
                $contidb = $conti1.Split(":")[0]            
                $db = [MongoDB.Driver.MongoDatabase]::Create("mongodb://" + $contidb + ":27017/$dbName")
                $collection = $db[$collectionName]
                $query = [MongoDB.Driver.Builders.Query]::EQ('username',"corp\" + $Member.samaccountname.ToLower() )
                $results = $collection.find($query)
                foreach ($result in $results) 
                {
                    $uid = $result[“username”]                           
                }
                if ($uid -ne $null)
                {
                    $body = (ConvertTo-Json @{                                        
                                            'user' = "corp\" + $Member.samaccountname.ToLower()                                        
                                            'groups' = $adgroup})            
                    $requestURI = $uri + "api/update_user?token=" + $token 
                    $response = Invoke-RestMethod -Uri $requestURI -ContentType "application/json" -Method Post -Body $body -Verbose                       
                    $uid = $null                                                   
                }
                else
                {                                          
                    if (![string]::IsNullOrEmpty($Member.PrimarySmtpAddress))
                    {
                        if($adgroup -eq "RL.APP.Admin" -Or $adgroup -eq "RL.APP.Dev")
                        {
                            $role = "Developer"
                        }                     
                        else
                        {
                            $role = "User"
                        }  
                        $body = (ConvertTo-Json @{
                                            'name' = $Member.name
                                            'user' = "corp\" + $Member.samaccountname.ToLower()
                                            'email' = $Member.PrimarySmtpAddress.ToString()
                                            'role' = $role
                                            'status' = "enabled"
                                            'authtype' = "ldap"
                                            'groups' = $adgroup})    
                                                                  
                        $requestURI = $uri + "api/create_user?token=" + $token 
                        #write-host $body                            
                        $response = Invoke-RestMethod -Uri $requestURI -ContentType "application/json" -Method Post -Body $body -verbose
                        if ($response -eq "@{ErrorCode=; ErrorDetail=; ErrorMessage=; Method=create_user; Response=}")
                        {       
                            # Send email to user
                            $From = "donotreplycontinuum@xyz.com"
                            $To = $email                           
                            $Subject = "Continuum login information"
                            $Body = "<b><font color=blue>Teammate,</b></font> <br>"
                            $Body += "<br>"
                            $Body += "Version 1 Continuum is the product that has been selected to manage the main orchestrations for all DevOps CI/CD builds and deployments. <br>"
                            $Body += "<br>"
                            $Body += "During certain phases of the process, an approval may be required for deployments into certain environments and after receiving an email of such required action, you will be required to log into Continuum to acknowledge and approve such deployments; therefore, we have created the login credentials for you that is required. <br>"
                            $Body += "<br>"
                            $Body += "If you need additional information, please reach out to your groups' DevOps leader for procedures related to your specific groups' DevOps processes. <br>"  
                            $Body += "<br>"
                            $Body += "<b><font color=blue>Please use the following login information for accessing Version 1 Continuum.</b></font> <br>"
                            $Body += "Username:   $username <br>"
                            $Body += "Password:   Your Active Directory domain account password.<br>"
                            $Body += "Login URL:  $uri <br>"
                            $Body += "<br>"
                            $Body += "Thank You <br>"
                            $Body += "DevOps Team <br>"
                            $SMTPServer = "imx.xyz.com"
                            $SMTPPort = "25"
                            write-host $Body
                            Send-MailMessage -From $From -to $To -Subject $Subject -Body $Body -BodyAsHtml  -SmtpServer $SMTPServer -port $SMTPPort
                        }
                    }
                }                                           
            }
        }
}        
