# Quarkus Web Bundler

[![Version](https://img.shields.io/maven-central/v/io.quarkiverse.web-bundler/quarkus-web-bundler?logo=apache-maven&style=flat-square)](https://search.maven.org/artifact/io.quarkiverse.web-bundler/quarkus-web-bundler)

- It relies on https://esbuild.github.io/ to bundle scripts (js, ts, jsx, tsx) and style (css, scss, sass) and make them easily available in the templates
- It supports dev mode and all
- It also deals with web dependencies (mvnpm and webjars). They can be used by just adding deps to the pom.
- It features a preset to create server side web components (Qute + Script + Style)
![quarkus-web-bundler.png](./quarkus-web-bundler.png?raw=true)