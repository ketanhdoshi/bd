# bd
## Big Data End-to-end Pipeline

This application consists of the following components:

## Data Sources
Typically, you would have data from a variety of sources and in different formats. The goal is to be able to access this in a uniform way using a standard set of tools, data platforms and infrastructure.

Secondly this data is never static. It is a continuously changing based on events in your application or business. In other words, we can treat this data as a continuous stream of data.

- File Data: This might be unstructured data stored in documents, or structured data in files of different formats such as log files, JSON, CSV, Parquet or any number of other formats.

- SQL Database: This would usually be operational data from a OLTP web application, a third-party SaaS application, for example. This data can be fetched using a Change Data Capture (CDC) tool.

- Data from external web services: This could be data from third-party web services, or a hosted SaaS application. It is typically accessible via an interface such as a REST API.

- User Event data: Data collected from your web site, or mobile app based on user interactions such as clickstream data etc

- IoT Data: This might be data from different gadgets such as sensors. It is usually very high volume data.

## Data Pipeline
Consists of two components - Streaming and Big Data Processing

The traditional approach is to build ETL workflows that take data from the various Sources and structure it for use by the Destinations. This often consists of point-to-point jobs that are very specifically tailored for a specific source and specific destination.

This can result in a spaghetti of point-to-point connections. There is very little reuse possible between jobs.

### Streaming Platform
Data from all these different sources is ingested using Kafka Connect Connectors. Each Connector is intended to fetch data from a specific type of data source.

- SQL Database: Change Data Capture Connector by Debezium
- IoT Data: 

### Big Data Processing
Spark

Both raw data as well as processed data goes back to the stream. So a typical consumer of the data would not need to care whether the data they were consuming was originally obtained from a user application, or enriched and processed data by an upstream processing application.

Typically every application would be a consumer and a producer. Every application consumes data, processes it and produces result data. This result data goes back into the stream. 

## Data Destination
- Data Warehouses and Data Lakes: these are Repositories of data
- Analytics
  - Reporting Dashboards
  - Interactive BI Tools for ad-hoc analysis
- Machine Learning applications
- Results Consumed by web applications for presenting to end-users


### aaa

Batch Data Processing can be thought of as a simplified special case of Streaming Data. A batch is created by segmenting an otherwise continous data stream by defining start and stop boundaries. These boundaries are often based on time periods of interest for the application such as an hour, a day or a week. In some cases, the time boundaries might be arbitrary demarcations to keep the data volume manageable.

## Data Pipeline
There are many different ways to build this data pipeline.

### ETL
One typical approach is to build custom application-specific pipelines that connects up a particular data source (or set of sources) to the required data destination. This usually consists of a number of ETL jobs along with workflow to coordinate the steps between these jobs. The challenge is that this results in a large number of point-to-point connections. This makes it hard to reuse functionality and becomes hard to maintain.

### Data Warehouse or Data Lake
Another popular approach centers around a data warehouse, or data lake. In both cases, you have a central repository of all your data. Data from all the sources is brought into the data lake. All the destination applications then retrieve data from this central store and process it as needed.
- With a data warehouse, you typically define the schemas and structure of the warehouse up-front. And it is the responsibility of the ETL job to transform the source data to conform to the schema. This is often called 'Schema on Write'. 
- With a data lake, the structure is defined much more loosely. Data is brought in from the source system with a minimal amount of transformation. It is the job of the retrieving application to transform and structure the data at query time, as required by its intended purpose. This is often called 'Schema on Read'.

### Streaming Platform
This is an alternate approach that puts a Streaming system at the center. The data warehouse or data lake now becomes just another data destination. The benefit of this approach is that all data is available in real-time with low latency. This makes it suitable for both real-time and batch use cases. Another benefit is that it makes no distinction between the original data and enriched, processed data. Any application that processes data can send its results back into the data stream. Both are then available to all downstream applications. Another benefit is that it treats all data in your organisation as a continuous stream of business actions. It blurs the distinction between what is typically thought of as 'static, batch data' (such as in an operational database) and 'real-time data' (from event streams). All data, in effect is real-time streaming data.



