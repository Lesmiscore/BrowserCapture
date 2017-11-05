FROM ubuntu
MAINTAINER nao20010128nao

RUN mv /bin/sh /bin/sh.0 && \
    ln -s /bin/bash /bin/sh

RUN apt-get update && \
    apt-get install -y ca-certificates wget fonts-takao-pgothic xvfb zip unzip curl && \
    wget -q -O - https://dl-ssl.google.com/linux/linux_signing_key.pub | apt-key add - && \
    wget -q -O - http://deb.opera.com/archive.key | apt-key add - && \
    echo "deb http://dl.google.com/linux/chrome/deb/ stable main" > /etc/apt/sources.list.d/google.list && \
    echo "deb http://deb.opera.com/opera-stable/ stable non-free" > /etc/apt/sources.list.d/opera.list && \
    apt-get update && \
    DEBIAN_FRONTEND=noninteractive apt-get install -y opera-stable google-chrome-stable firefox openjdk-8-jdk && \
    rm -rf /var/lib/apt/lists/* && \
    wget -q -O /etc/fonts/local.conf https://github.com/fearphage/docker-opera/raw/master/local.conf && \
    useradd -ms /bin/bash lesmi

USER lesmi
WORKDIR /home/lesmi

ADD start.groovy /home/lesmi/

RUN wget -q -O - get.sdkman.io | bash && \
    source "/home/lesmi/.sdkman/bin/sdkman-init.sh" && \
    sdk install groovy && \
    groovy -version && \
    cat /home/lesmi/start.groovy | head -n3 > pre-grab.groovy && \
    groovy pre-grab.groovy && \
    wget -q -O /tmp/chromedriver.zip "http://chromedriver.storage.googleapis.com/2.29/chromedriver_linux64.zip" && \
    unzip -j /tmp/chromedriver.zip && rm /tmp/chromedriver.zip && \
    wget -q -O - "https://github.com/mozilla/geckodriver/releases/download/v0.19.1/geckodriver-v0.19.1-linux64.tar.gz" | tar xz geckodriver -O > geckodriver && \
    chmod a+x chromedriver geckodriver

ENTRYPOINT ["xvfb-run"]
CMD ["/home/lesmi/.sdkman/candidates/groovy/current/bin/groovy","/home/lesmi/start.groovy"]
EXPOSE 8080
