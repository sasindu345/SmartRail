# certs/

Development-only TLS keystores live here. They are **generated locally** by Member 4's
`scripts/generate-dev-certs.sh` and are **git-ignored**. Never commit `*.p12`, `*.jks` or passwords.

Paths and password environment-variable names are configured in `config/smartrail.properties`
(`tls.keystore.path`, `tls.truststore.path`, `tls.*.password.env`).
