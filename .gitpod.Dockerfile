FROM gitpod/workspace-full

USER root

RUN bash -c ". /home/gitpod/.sdkman/bin/sdkman-init.sh \
             && sdk install java 18.0.1-amzn \
             && sdk default java 18.0.1-amzn"