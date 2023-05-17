# Quarkus Web Bundler

[![Version](https://img.shields.io/maven-central/v/io.quarkiverse.web-bundler/quarkus-web-bundler?logo=apache-maven&style=flat-square)](https://search.maven.org/artifact/io.quarkiverse.web-bundler/quarkus-web-bundler)

> :warning: **This is not yet stable, work is in progress**: The dev experience might not be perfect and you might find many bugs, please report them..

Create full-stack web apps quickly and easily with this Quarkus extension. It offers zero-configuration bundling for your web app scripts (JS, JSX, TS, TSX), dependencies (jQuery, React, htmx, etc.), and styles (CSS, SCSS, SASS).

- It relies on https://esbuild.github.io/ to bundle scripts (js, ts, jsx, tsx) and style (css, scss, sass) and make them easily available in the templates
- It supports dev mode and all
- It also deals with web dependencies (mvnpm and webjars). They can be used by just adding deps to the pom.
- It features a preset to create server side web components (Qute template + Script + Style)


### Bye bye package.json

![bye bye](https://media4.giphy.com/media/w89ak63KNl0nJl80ig/giphy.gif?cid=ecf05e47e4m3b8izbl3oc7yzpnjulxge9nnpmgwsobqg8vlk&ep=v1_gifs_search&rid=giphy.gif&ct=g)

The pom.xml contains the web dependencies through mvnpm or webjars (jquery, htmx, react, ...).


### How it works

![quarkus-web-bundler.png](./quarkus-web-bundler.png?raw=true)


### Examples


Look in `src/main/resources/web` for the web-app
```
app/: app scripts and styles)
components/: server side web-components
static/: static files served under http://localhost:8080/static/...
```

- https://github.com/ia3andy/quarkus-bundler-react (quarkus, web-bundler, react)
- https://github.com/ia3andy/renotes (quarkus, web-bundler, renarde, htmx)
- https://github.com/ia3andy/web-bundler-jquery (quarkus, web-bundler, jquery, bootstrap, bootstrap-icons)
- https://github.com/ia3andy/bundler-gradle-jquery (quarkus, web-bundler, gradle, jquery, bootstrap, bootstrap-icons)
