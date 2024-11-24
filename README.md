# cert-checker

A small plugin for icinga to notify certificates about to expire

This project uses Quarkus, the Supersonic Subatomic Java Framework.

If you want to learn more about Quarkus, please visit its website: <https://quarkus.io/>.

## Running the application in dev mode

You can run your application in dev mode that enables live coding using:

```shell script
./mvnw compile quarkus:dev
```

## Packaging and running the application

The application can be packaged using:

```shell script
./mvnw package
```

It produces the `quarkus-run.jar` file in the `target/quarkus-app/` directory.
Be aware that it’s not an _über-jar_ as the dependencies are copied into the `target/quarkus-app/lib/` directory.

The application is now runnable using `java -jar target/quarkus-app/quarkus-run.jar`.

If you want to build an _über-jar_, execute the following command:

```shell script
./mvnw package -Dquarkus.package.jar.type=uber-jar
```

The application, packaged as an _über-jar_, is now runnable using `java -jar target/*-runner.jar`.

## Creating a native executable

You can create a native executable using:

```shell script
./mvnw package -Dnative
```

Or, if you don't have GraalVM installed, you can run the native executable build in a container using:

```shell script
./mvnw package -Dnative -Dquarkus.native.container-build=true
```

You can then execute your native executable with: `./target/cert-checker-0.0.1-SNAPSHOT-runner`

If you want to learn more about building native executables, please consult <https://quarkus.io/guides/maven-tooling>.

## Configuration for Icinga
1. Copy the native binary to `PluginContribDir` (Debian: `/usr/lib/nagios/plugins`) or use the `*.jar` file and modify `command` below accordingly.
2. Add the following to the icinga configuration
   ```
   object CheckCommand "certificate" {
     command = [ PluginContribDir + "/check_certificate" ]
   
     arguments = {
       "--host" = "$certificate_host$"
       "--port" = "$certificate_port$"
       "--subject" = "$certificate_subject$"
       "--expire-days-warning" = "$certificate_expire_days_warning$"
       "--expire-days-error" = "$certificate_expire_days_error$"
       "--starttls" = {
         set_if = "$certificate_starttls$"
         value = "$certificate_starttls_mode$"
       }
     }
   
     vars.certificate_host = "$address$"
     vars.certificate_port = 443
     vars.certificate_subject = "$certificate_host$"
     vars.certificate_expire_days_warning = 30
     vars.certificate_expire_days_error = 10
     vars.certificate_starttls = false
     vars.certificate_starttls_mode = "smtp"
   }
   ```
3. Add services, for example:
   ```
   apply Service "webserver-certificate" {
     import "overridable-times-service"
     check_command = "certificate"
     
     assign where "webserver-https" in host.groups
   }
   
   apply Service "imap-143-certificate" {
     import "generic-service"
     check_command = "certificate"

     vars.certificate_port = 143
     vars.certificate_starttls = true
     vars.certificate_starttls_mode = "imap"
     vars.certificate_subject = "mail.daho.at"

     assign where "imap" in host.groups
   }
   ```


[Related guide section...](https://quarkus.io/guides/getting-started-reactive#reactive-jax-rs-resources)
