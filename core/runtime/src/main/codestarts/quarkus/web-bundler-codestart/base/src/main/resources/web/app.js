// See https://docs.quarkiverse.io/quarkus-web-bundler/dev/advanced-guides.html#web-dependencies
// for more information about how to import web-dependencies:

// Example:
// in your pom.xml:
// <dependency>
// 	<groupId>org.mvnpm</groupId>
// 	<artifactId>jquery</artifactId>
// 	<version>3.7.1</version>
// 	<scope>provided</scope>
// </dependency>
//
// in this file:
// import $ from 'jquery'

// This app will be bundled by the Web-Bundler (including the imported libraries) and available using the {#bundle /} tag
// for more information about how to use the {#bundle /} tag, see https://docs.quarkiverse.io/quarkus-web-bundler/dev/advanced-guides.html#bundle-tag
// Sets the number of stars we wish to display

const numStars = 1000;

// For every star we want to display
for (let i = 0; i < numStars; i++) {
    const star = document.createElement("div");
    star.className = "star";
    const xy = getRandomPosition();
    star.style.left = xy[0] + 'px';
    star.style.bottom = xy[1] + 'px';
    document.body.append(star);
}

// Gets random x, y values based on the size of the container
function getRandomPosition() {
    const width = window.innerWidth;
    const height = window.innerHeight;
    const randomX = Math.floor(Math.random()*width);
    const randomY = Math.floor(Math.random()*height);
    return [randomX,randomY];
}