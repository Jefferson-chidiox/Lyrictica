const query = `
query {
  allProcessedTracks(first: 1) {
    nodes {
      id
      title
      lossyAudioUrl
      lossyArtworkUrl
      duration
      artistByArtistId {
        name
      }
    }
  }
}`;

fetch("https://api.spinamp.xyz/v3/graphql", {
  method: "POST",
  headers: {
    "Content-Type": "application/json"
  },
  body: JSON.stringify({ query })
})
.then(res => res.json())
.then(data => {
  if (data.errors) {
    console.error(data.errors);
  } else {
    console.log(JSON.stringify(data.data.allProcessedTracks.nodes[0], null, 2));
  }
})
.catch(err => console.error(err));
