\usepackage{amsmath}
# Quarkus Web Bundler

[![Version](https://img.shields.io/maven-central/v/io.quarkiverse.web-bundler/quarkus-web-bundler?logo=apache-maven&style=flat-square)](https://search.maven.org/artifact/io.quarkiverse.web-bundler/quarkus-web-bundler)

Create full-stack web apps and components with this Quarkus extension. It offers zero-configuration bundling and minification (with source-map) for your web app scripts (JS, JSX, TS, TSX), dependencies (jQuery, htmx, Bootstrap, Lit etc.), and styles (CSS, SCSS, SASS).

* [x] Production build
* [x] Awesome Dev experience
* [x] Integrated with NPM dependencies through [mvnpm](https://docs.quarkiverse.io/quarkus-web-bundler/dev/advanced-guides.html#mvnpm) or [webjars](https://docs.quarkiverse.io/quarkus-web-bundler/dev/advanced-guides.html#webjars).
* [x] Build-time index.html rendering with bundled scripts and styles
* [x] Server Side Qute Components (Qute template + Script + Style)

**Is it the same as Quinoa?** It is very close but:

- It is already integrated with a bundler (esbuild, which is very similar to Webpack or Rollup)
- NodeJS is not needed
- It works without any configuration
- All the npm catalog is available directly as dependencies in your pom.xml/build.gradle

**When should I use Quinoa instead?**

- I have a dedicated UI team very familliar with the NodeJS eco-system
- I need a very specific NodeJs/Bundling configuration that the web-bundler does not cover

**How to test my Web App without Jest or similar?**

Use `@QuarkusTest` with [https://docs.quarkiverse.io/quarkus-playwright/dev/](Quarkus Playwright).
It is very easy to create full-stack tests to cover all your scenarios (and re-using all the backend testing data).

**Can I swith from Quinoa to the Web Bundler?**

Yes, just move your web-dependencies to the pom.xml/build.gradle and follow the web-bundler structure, the output app should be the same.

**Can I swith from the Web Bundler to Quinoa?**

Yes, you just need to use a framework (like Vite) and switch to a package.json and follow your framework structure, the output app should be the same.



All the information you need to use Quarkus Web Bundler is in the [user documentation](https://docs.quarkiverse.io/quarkus-web-bundler/dev/).


