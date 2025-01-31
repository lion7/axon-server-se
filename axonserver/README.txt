This is the Axon Server Standard Edition, version 4.1.7

For information about the Axon Framework and Axon Server,
visit https://docs.axoniq.io.

Release Notes for version 4.1.7
-------------------------------

* Use info endpoint to retrieve version number and product name
* Reset reserved sequence numbers for aggregate when storing the event failed

Release Notes for version 4.1.6
-------------------------------

* Added operation to set cached version numbers for aggregates

Release Notes for version 4.1.5
-------------------------------

* Fix for authorization path mapping and improvements for rest access control
* Improvements in release procedure for docker images
* Fix for subscription query memory leak
* Improvements in error reporting in case of disconnected applications
* Improvements in detection of insufficient disk space

Release Notes for version 4.1.4
-------------------------------
* Fix for appendEvent with no events in stream

Release Notes for version 4.1.3
-------------------------------
* CLI commands now can be performed locally without token.

Release Notes for version 4.1.2
-------------------------------

* Status displayed for tracking event processors fixed when segments are running in different applications
* Tracking event processors are updated in separate thread
* Logging does not show application data anymore
* Changed some gRPC error codes returned to avoid clients to disconnect when no command handler found for a command

Release Notes for version 4.1.1
-------------------------------

* Sources now available in public GitHub repository
* Merge tracking event processor not always available when it should
* Logging changes
* GRPC version update

Release Notes for version 4.1
-------------------------------

* Added split/merge functionality for tracking event processors

Release Notes for version 4.0.4
-------------------------------

* Fix for check on sequence numbers with large gap

Release Notes for version 4.0.3
-------------------------------

* Support for Java versions 10 and 11
* Actuator endpoints no longer require AxonIQ-Access-Token when access control enabled

Release Notes for version 4.0.2
-------------------------------

* Performance improvements for event replay.
* Changes in the event filename format to match AxonDB filenames

Running Axon Server
===================

By default the Axon Framework is configured to expect a running Axon Server
instance, and it will complain if the server is not found. To run Axon Server,
you'll need a Java runtime (JRE versions 8 through 11 are currently supported).
A copy of the server JAR file has been provided in the demo package.
You can run it locally, in a Docker container (including Kubernetes or even Minikube),
or on a separate server.

Running Axon Server locally
---------------------------

To run Axon Server locally, all you need to do is put the server JAR file in
the directory where you want it to live, and start it using:

    java -jar axonserver.jar

You will see that it creates a subdirectory data where it will store its
information. Open a browser to with URL http://localhost:8024 to view the dashboard.

Running Axon Server in a Docker container
-----------------------------------------

To run Axon Server in Docker you can use the image provided on Docker Hub:

    $ docker run -d --name my-axon-server -p 8024:8024 -p 8124:8124 axoniq/axonserver
    ...some container id...
    $

WARNING:
    This is not a supported image for production purposes. Please use with caution.

If you want to run the clients in Docker containers, and are not using something
like Kubernetes, use the "--hostname" option of the docker command to set a useful
name like "axonserver":

    $ docker run -d --name my-axon-server -p 8024:8024 -p 8124:8124 --hostname axonserver axoniq/axonserver

When you start the client containers, you can now use "--link axonserver" to provide
them with the correct DNS entry. The Axon Server-connector looks at the
"axon.axonserver.servers" property to determine where Axon Server lives, so don't
forget to set that to "axonserver" for your apps.

Running Axon Server in Kubernetes and Minikube
-----------------------------------------------

WARNING:
    Although you can get a pretty functional cluster running locally using Minikube,
    you can run into trouble when you want to let it serve clients outside of the
    cluster. Minikube can provide access to HTTP servers running in the cluster,
    for other protocols you have to run a special protocol-agnostic proxy like you
    can with "kubectl port-forward <pod-name> <port-number>".
    For non-development scenarios, we don't recommend using Minikube.

Deployment requires the use of a YAML descriptor, an working example of which
can be found in the "kubernetes" directory. To run it, use the following commands
in a separate window:

    $ kubectl apply -f kubernetes/axonserver.yaml
    statefulset.apps "axonserver" created
    service "axonserver-gui" created
    service "axonserver" created
    $ kubectl port-forward axonserver-0 8124
    Forwarding from 127.0.0.1:8124 -> 8124
    Forwarding from [::1]:8124 -> 8124


You can now run your app, which will connect throught the proxied gRPC port. To see
the Axon Server Web GUI, use "minikube service --url axonserver-gui" to obtain the
URL for your browser. Actually, if you leave out the "--url", minikube will open
the the GUI in your default browser for you.

To clean up the deployment, use:

    $ kubectl delete sts axonserver
    statefulset.apps "axonserver" deleted
    $ kubectl delete svc axonserver
    service "axonserver" deleted
    $ kubectl delete svc axonserver-gui
    service "axonserver-gui" deleted

Use "axonserver" (as that is the name of the Kubernetes service) for your clients
if you're going to deploy them in the cluster, which is what you'ld probably want.
Running the client outside the cluster, with Axon Server inside, entails extra
work to enable and secure this, and is definitely beyond the scope of this example.

Configuring Axon Server
=======================

Axon Server uses sensible defaults for all of its settings, so it will actually
run fine without any further configuration. However, if you want to make some
changes, below are the most common options. You can change them using an
"axonserver.properties" file in the directory where you run Axon Server. For the
full list, see the Reference Guide. https://docs.axoniq.io/reference-guide/axon-server

* axoniq.axonserver.name
  This is the name Axon Server uses for itself. The default is to use the
  hostname.
* axoniq.axonserver.hostname
  This is the hostname clients will use to connect to the server. Note that
  an IP address can be used if the name cannot be resolved through DNS.
  The default value is the actual hostname reported by the OS.
* server.port
  This is the port where Axon Server will listen for HTTP requests,
  by default 8024.
* axoniq.axonserver.port
  This is the port where Axon Server will listen for gRPC requests,
  by default 8124.
* axoniq.axonserver.event.storage
  This setting determines where event messages are stored, so make sure there
  is enough diskspace here. Losing this data means losing your Events-sourced
  Aggregates' state! Conversely, if you want a quick way to start from scratch,
  here's where to clean.
* axoniq.axonserver.snapshot.storage
  This setting determines where aggregate snapshots are stored.
* axoniq.axonserver.controldb-path
  This setting determines where the message hub stores its information.
  Losing this data will affect Axon Server's ability to determine which
  applications are connected, and what types of messages they are interested
  in.
* axoniq.axonserver.accesscontrol.enabled
  Setting this to true will require clients to pass a token.
* axoniq.axonserver.accesscontrol.token
  This is the token used for access control.

The Axon Server HTTP server
===========================

Axon Server provides two servers; one serving HTTP requests, the other gRPC.
By default these use ports 8024 and 8124 respectively, but you can change
these in the settings as described above.

The HTTP server has in its root context a management Web GUI, a health
indicator is available at "/actuator/health", and the REST API at "/v1'. The
API's Swagger endpoint finally, is available at "/swagger-ui.html", and gives
the documentation on the REST API.
