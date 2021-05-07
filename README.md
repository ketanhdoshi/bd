# bd
## Big Data End-to-end Pipeline

An experimental project to create an end-to-end real-time streaming pipeline for Big Data and Machine Learning, using open-source platforms and tools.

It focuses on a common problem when dealing with data in an organization:
- We have a number of disparate Data Sources: they have reside in a multitude of source systems and have different formats. They volume and velocity (ie. rate at which it is generated and consumed) can vary.
- We have a number of different Data Destinations: data can be consumed and processed in many ways and can be used for different purposes.
- We need to build a Data Pipeline: this sits in the middle and connects the data sources with the data destinations.

### Data Sources
It uses a few representative examples of the kinds of data sources that are typically found in an organization.

- **MySQL Database**: This could be operational data from a OLTP web application, a third-party SaaS application.
- **Parquet File Data**: This is a structured format with a schema. In a typical system, you might also have other structured or semi-structured formats like log files, JSON, CSV or unstructured data stored in documents.
- **User Event Clickstream**: This could be data collected from your web site, or mobile app such as clickstream data.
- **Mosquitto MQTT Server for IoT Data**: This might be data from different gadgets such as sensors, and is often very high volume data.
- **External Web Service via REST API**: This could be data from third-party web services, or a hosted SaaS application.

### Data Destination
Similarly, it uses an example data destination.

- **Elasticsearch-Kibana** real-time dashboard for analytics

Other commonly occurring destinations could be:

- Data Warehouses and Data Lakes: these are Repositories of all of the organization's data.
- Analytics Applications:
  - Reporting Dashboards
  - Interactive BI Tools for ad-hoc analysis
- Machine Learning applications
- Result Caches: Results from processing jobs meant to be consumed by web applications for presenting to end-users

## Data Pipeline
The traditional approach is to build ETL workflows that take data from the various Sources and structure it for use by the Destinations. This often consists of point-to-point jobs that are very specifically tailored for a specific source and specific destination.

Instead, here we use an alternative approach, of creating a unified data pipeline. It acts as a single channel for connecting all sources and destinations. It becomes a common integration platform for all data in the organization.

It consists of two primary components - Streaming and Big Data Processing.

### Streaming Platform
Kafka is used as the foundation for all streaming data. Kafka is deployed as part of the Confluent Platform:
- Kafka broker with Zookeeper
- Kafka Connect - for getting data into and out of Kafka
- Schema Registry: for storing schema definitions and versions of data flowing through Kafka topics.
- KSQL
- Control Center: management console for administration of cluster components.

Streaming data includes both the raw data ingested from the data sources as well as processed results computed by data processing jobs. Two formats are used for the data in Kafka topics - JSON and Avro. Avro data can have two formats - the standard Avro format which contains the schema definition and Confluent's Avro format, which contains only the schema ID while the schema definition resides in the Schema Registry.

### Data Connectors
Kafka Connect Connectors are used to fetch data from the data sources and to send data to the data destinations. 

We use these connectors to ingest data:
- Debezium Change Data Capture (CDC) Connector: captures each MySQL insert and update transaction as a Change Event
- Datagen Connector: simulates User Clickstream Events based on a custom-defined schema
- MQTT Connector: fetches IoT data from Mosquitto MQTT server
- Custom Connector: fetches data from an external service using the OpenWeather REST API

Send data to data destinations:
- Elasticsearch Connector: Processed results are sent to Elasticsearch for visualization with Kibana

### Big Data Processing 
Spark is used for processing the streaming data. It reads the data from Kafka topics, performs its computations and writes its output data back into Kafka. These results are now available for other Spark applications to consume.

Hence, the stream contains both the raw data from the data sources as well as enriched and processed data. It looks the same to a consuming Spark application. Hence, all data in the organization is available to all applications, allowing them to build on top of one another.

## Deployment
All components are deployed using Docker containers and Kubernetes. This allows you to deploy this whole system on a single laptop during development.

It also gives you the flexibility to use the same setup for your production deployment, which can be deployed either on the Cloud, on-Prem or as a hybrid.

All open source platform components are deployed to Kubernetes using the accompanying Helm Charts
- MySQL
- ElasticSearch and Kibana
- Mosquitto
- Confluent Platform
- Spark Operator

## Development Environment
Development is also done using containers using VSCode's remote development functionality. No development tools have to be installed on the host laptop other than Docker and VSCode. For example, the following development can all be done using containers:
- Spark applications in Scala and Python
- Kafka consumers and producers in Java and Maven
- Kafka Connect custom connectors in Java and Maven

### Connector development
- Kafka Connect - two clusters are used with two configurations - one in Kubernetes that is used as a 'production' deployment and the other on a manually launched Docker Compose environment for custom connector development.

### Demo Application
It uses a demo application to showcase the components of the pipeline. The use case is digital TV, where shows are broadcast on a number of channels based on a schedule. Users can view those shows from a number of devices from different locations. All user actions such as starting and stopping shows, and switching channels are tracked. Ads are broadcast on different shows at different times. We want to track which shows and which ads were viewed by which users and for how long. This allows us to do analytics based on various factors including user demographic data and location.
