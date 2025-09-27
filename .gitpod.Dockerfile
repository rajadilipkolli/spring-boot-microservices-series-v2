FROM gitpod/workspace-java-21

USER root

RUN bash -c ". /home/gitpod/.sdkman/bin/sdkman-init.sh \
             && sdk install java 25-tem \
             && sdk default java 25-tem"