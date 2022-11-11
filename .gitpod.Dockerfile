FROM gitpod/workspace-full

USER root

RUN bash -c ". /home/gitpod/.sdkman/bin/sdkman-init.sh \
             && sdk install java 22.3.r17-nik \
             && sdk default java 22.3.r17-nik"