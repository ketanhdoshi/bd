# Base image with Java and utilities to download/extract Spark
FROM kd/miniconda:latest

ENV SPARK_VERSION 2.4.3
ENV SPARK_INSTALL /usr/local
ENV SPARK_HOME $SPARK_INSTALL/spark
ENV SPARK_ROLE master
ENV HADOOP_VERSION 2.7
ENV SPARK_EXTRACT_DIR spark-2.4.3-bin-hadoop2.7
ENV SPARK_TGZ $SPARK_EXTRACT_DIR.tgz

# Download Spark tarfile
#RUN curl -s https://archive.apache.org/dist/spark/spark-2.4.3/spark-2.4.3-bin-hadoop2.7.tgz

# Copy the Spark tarfile
COPY ./$SPARK_TGZ /

# Install Spark by extracting and moving to the right directory
RUN tar -xzf /$SPARK_TGZ && \
    mv $SPARK_EXTRACT_DIR $SPARK_HOME && \
    rm /$SPARK_TGZ

# Set the working dir so we land there when starting any interactive bash in the container
WORKDIR $SPARK_HOME