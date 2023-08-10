import $ from 'jquery';

$(document).ready(() => {
    const $message = $('#message');
    setTimeout(() => {
        $message.html("Unleash the script!!!!").css("font-weight", "bold");
        console.log("Message has been defined with Jquery");
    }, 200);

});
