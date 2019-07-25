# Base image with Java (for Spark) and Debian Linux
FROM openjdk:8

ENV LANG=C.UTF-8 LC_ALL=C.UTF-8
ENV CONDA_INSTALL /usr/local/conda
ENV PATH $CONDA_INSTALL/bin:$PATH

RUN apt-get update --fix-missing && \
    apt-get install -y wget bzip2 ca-certificates curl git && \
    apt-get clean && \
    rm -rf /var/lib/apt/lists/*

RUN wget --quiet https://repo.anaconda.com/miniconda/Miniconda3-latest-Linux-x86_64.sh -O ~/miniconda.sh && \
    /bin/bash ~/miniconda.sh -b -p $CONDA_INSTALL && \
    rm ~/miniconda.sh && \
    $CONDA_INSTALL/bin/conda clean -tipsy && \
    ln -s $CONDA_INSTALL/etc/profile.d/conda.sh /etc/profile.d/conda.sh && \
    echo ". $CONDA_INSTALL/etc/profile.d/conda.sh" >> ~/.bashrc && \
    echo "conda activate base" >> ~/.bashrc

CMD [ "/bin/bash" ]