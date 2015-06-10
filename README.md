# SMS with Delivery Receipt

Simple Servlet for handling SMS and Delivery Receipt event using the [Bandwidth Java SDK](https://github.com/bandwidthcom/java-bandwidth).


## Getting started
You need to have

    - Git
    - Java Runtime Edition 1.7
    - Bandwidth Application Platform account and allocated number
    - Heroku account and installed Heroku Toolbelt
    - ngrok tool for local tests
    - Maven 3


## Setup

#### Clone the repository
```console
git clone https://github.com/bandwidthcom/java-bandwidth-sms-dlr-example.git
cd java-bandwidth-sms-dlr-example
```

#### Setup the client SDK environment with your Catapult credentials:
```console
export BANDWIDTH_API_ENDPOINT='https://api.catapult.inetwork.com'
export BANDWIDTH_API_VERSION='v1'
export BANDWIDTH_USER_ID=<your-user-id>
export BANDWIDTH_API_TOKEN=<your-token>
export BANDWIDTH_API_SECRET=<your-secret>
export ALLOCATED_NUMBER=<your-allocated-number>
```

#### Initialize git
```console
git init
git add .
git commit -m "My sms with dlr app"
```

##  Deploying to Heroku

#### Create a new Heroku application:
```console
heroku apps:create
> Created http://<your-app>.herokuapp.com/ | git@heroku.com:<your-app>.git
'''

You may also see:
'''
> Git remote heroku added
```
This means a git remote called Heroku was automatically added to your git repository.  If so you may skip the next step.

#### Add remote git repository
```console
git remote add heroku git@heroku.com:<your-app>.git
```

#### Setup the Heroku environment variables:

Bandwidth credentials:
```console
heroku config:set BANDWIDTH_API_ENDPOINT=$BANDWIDTH_API_ENDPOINT --app <your-app-name>
heroku config:set BANDWIDTH_API_VERSION=$BANDWIDTH_API_VERSION --app <your-app-name>
heroku config:set BANDWIDTH_USER_ID=$BANDWIDTH_USER_ID --app <your-app-name>
heroku config:set BANDWIDTH_API_TOKEN=$BANDWIDTH_API_TOKEN --app <your-app-name>
heroku config:set BANDWIDTH_API_SECRET=$BANDWIDTH_API_SECRET --app <your-app-name>
```

Web application settings:
```console
heroku config:set ALLOCATED_NUMBER=$ALLOCATED_NUMBER --app <your-app-name>
```

Push code to Heroku:
```console
git push heroku master
```

Add a web dyno
```console
heroku ps:scale web=1
```

Make sure that all went well:
```console
heroku logs -t
```


## Demo

#### Send an SMS with DLR:
```console
<your-browser-choice> http://<your-app>.heroku.com/demo/receipt?toNumber=<your-to-number>
```

It will:

1. Create a SMS from $ALLOCATED_NUMBER to the number specified on toNumber query parameter.
2. Show on browser the Message sent with all attributes.
3. Show on browser the Sent event captured via callback.
4. Show on browser the Delivery Receipt event captured via callback.


## Local Setup

#### Installing ngrok tool (For local tests):

Download [ngrok](https://ngrok.com/download).
```console
unzip /path/to/ngrok.zip
./ngrok http 8080
```

The ngrok will create a Forwarding DNS to your localhost. You will need the DNS to setup on environment variable.

#### Initiate the application
```console
mvn jetty:run
```

#### Send an SMS with DLR:
```console
<your-browser-choice> http://<ngrok-forwarding-dns>/demo/receipt?toNumber=<your-to-number>
