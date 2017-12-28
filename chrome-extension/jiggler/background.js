// Copyright (c) 2017 Yieldbot. All rights reserved.

function navigate(url) {
  chrome.tabs.query({active: true, currentWindow: true}, function(tabs) {
    chrome.tabs.update(tabs[0].id, {url: url});
  });
}

// This event is fired with the user accepts the input in the omnibox.
chrome.omnibox.onInputEntered.addListener(
  function(text) {
    // TODO edit this URL for your own deployment
    navigate('https://URL/' + text);
  });
