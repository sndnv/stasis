function sendAuthorization(uri, authorization, callback) {
    let request = new XMLHttpRequest();
    request.open('GET', uri, true)
    request.setRequestHeader('Authorization', authorization);
    request.onload = () => {
      let queryParams = new URL(request.responseURL).searchParams;
      if(queryParams.get('error') === 'access_denied') {
        callback('access_denied');
      } else {
        window.location.href = request.responseURL;
        callback(null);
      }
    };
    request.send(null);
}
