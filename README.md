# Quarkus Web Bundler

[![Version](https://img.shields.io/maven-central/v/io.quarkiverse.web-bundler/quarkus-web-bundler?logo=apache-maven&style=flat-square)](https://search.maven.org/artifact/io.quarkiverse.web-bundler/quarkus-web-bundler)

Create full-stack web apps and components with this Quarkus extension. It offers zero-configuration bundling and minification (with source-map) for your web app scripts (JS, JSX, TS, TSX), dependencies (jQuery, htmx, Bootstrap, Lit etc.), and styles (CSS, SCSS, SASS).

No need to install NodeJs, it relies on a Java wrapped version of [esbuild](https://esbuild.github.io/) to bundle scripts (js, ts, jsx, tsx) and styles (css, scss, sass) and make them easily available in the templates.

* [*] Production build
* [*] Awesome Dev experience
* [*] Integrated with NPM dependencies through [mvnpm](https://mvnpm.org) or [webjars](https://www.webjars.org/).
* [*] Server Side Web Components (Qute template + Script + Style)

### Examples


- https://github.com/ia3andy/quarkus-bundler-react (quarkus, web-bundler, react, react-bootstrap)
- https://github.com/ia3andy/renotes (quarkus, web-bundler, renarde, htmx)
- https://github.com/ia3andy/web-bundler-jquery (quarkus, web-bundler, jquery, bootstrap, bootstrap-icons)
- https://github.com/ia3andy/bundler-gradle-jquery (quarkus, web-bundler, gradle, jquery, bootstrap, bootstrap-icons)
