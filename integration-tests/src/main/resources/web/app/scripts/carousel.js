import $ from 'jquery'
import 'slick-carousel'
import 'slick-carousel/slick/slick.css'
import 'slick-carousel/slick/slick-theme.css'

$(document).ready(function(){
    $('.slick-carousel').slick({
        infinite: true,
        slidesToShow: 5, // Shows a three slides at a time
        slidesToScroll: 1, // When you click an arrow, it scrolls 1 slide at a time
        arrows: true, // Adds arrows to sides of slider
        dots: true, // Adds the dots on the bottom
        autoplay: true,
        autoplaySpeed: 2000,
    });
});
