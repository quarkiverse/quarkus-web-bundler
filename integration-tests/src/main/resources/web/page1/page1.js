import $ from 'jquery';

$(document).ready(() => {
    const $message = $('#message');
    setTimeout(() => {
        $message.html("This is page 1").css("font-weight", "bold");
        console.log("Message has been defined with Jquery");
    }, 200);
});
