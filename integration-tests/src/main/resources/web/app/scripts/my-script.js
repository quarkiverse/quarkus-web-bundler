import $ from 'jquery';

$(document).ready(() => {
    const $message = $('#message');
    setTimeout(() => {
        $message.html("Unleash the jason!!!!").css("font-weight", "bold");
        console.log("Message has been defined with Jquery");
    }, 2000);

});
