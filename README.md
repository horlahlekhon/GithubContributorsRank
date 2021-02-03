# github-contributor-ranks

This is an API that returns a list of contributors that have made active contributions to 1 or more projects across given organisation's github account.

### Structure
The API exposes a single endpoint at `host:8080/org/<organisation_name>/contributions`  which returned json formatted data sorted in descending order of 
most contributions. 
```json
[
    {
        "contributions": 1727,
        "name": "jroper"
    },
    {
        "contributions": 1361,
        "name": "robbyrussell"
    },
    {
        "contributions": 979,
        "name": "iluwatar"
    }
]
```

### Caching
There is a simple route based LFU cache implemented; not something serious and can be improved to work better. one reason for the cache
is to avoid making repeated calls to the github api, plus for organisations that have huge amount of repositories, we will be forced to 
make a lot of calls to the github API over a very short period of time and this affects response time of this api, so for big organisation, 
we only guarantee to take a bit for the first request and subsequent ones should be fast enough.

> ##### Side Note:
>      The github API will fail to return the full reposotories dataset for some really huge organisations like google who has repositories in excess of 1k
>      through the API. so data might be incomplete for repositories like such.

### How to run

Assuming the runner doesnt have scala or sbt installed on their computer, this steps should be taken to run the API.

1. Install scala and sbt 
    Please visit the following link to get scala and sbt installed.
    [Install scala & sbt]("https://docs.scala-lang.org/getting-started/index.html")
    
2. Clone the repository
    `git clone <repo_url>`

3. Set up github personal access token
    This token is used by github to increase amount of requests to the API per hour, without it we are only allowed to make 60 requests. 
    while adding it to the Authorized header allow us to make 5000 per hour.
    create this token on github and set it as value of the environmental variable `GH_TOKEN`.
    ```shell script
     export GH_TOKEN=<access_token>
    ```       
    
3. change directory into the cloned repo, pull dependencies and build
    ```shell script
     cd github-contributor-ranks
     sbt compile
    ```

4. After the success of step 3, we can then run the project with `sbt run` from normal powershell or unix shell or just `run` from the sbt shell

#### Test
To run the tests; do `sbt test` from the normal cli or `test` from the sbt prompt

### Improvements
Many things can be improved on the overall implementation. But, because of tight schedules and work constraints, plus time constraint of this test.
 i made sure to focus on delivering usable implementation in the given time frame. However, I might just improve it in the future.

1. The requests can be improved a bit using a akka http connection pools, something like `Http().cachedHostConnectionPoolHttps[T]`
    could be used with `Source.queue[]` instead of instantiating a new `Source` and connection everytime a request is made.

2. More tests.

3. For a more fine grain solution, the LFU cache should be improved as it also caches errors too until the `Cache-control`
   header is set to `no-cache`. this can be done by moving the caching layer to application logic instead of routing layer.