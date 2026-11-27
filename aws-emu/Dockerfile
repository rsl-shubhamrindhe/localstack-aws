FROM public.ecr.aws/amazonlinux/amazonlinux:2023-minimal

ARG TARGETARCH
RUN case "$TARGETARCH" in \
      amd64) rpm_arch=x86_64 ;; \
      arm64) rpm_arch=aarch64 ;; \
      *) exit 1 ;; \
    esac && \
    curl -fsSL "https://downloads.aws.com/aws-emulator/packages/aws-emulator/LTS/latest/aws-emulator-${rpm_arch}.rpm" -o /tmp/aws-emulator.rpm && \
    rpm -i /tmp/aws-emulator.rpm && rm /tmp/aws-emulator.rpm && \
    mkdir -p /home/emulator && chown 1000 /home/emulator

USER 1000
ENV HOME=/home/emulator EMULATOR_BIND_ADDRESS=0.0.0.0
ENTRYPOINT ["aws-emulator"]
