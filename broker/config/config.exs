# This file is responsible for configuring your application
# and its dependencies with the aid of the Config module.
#
# This configuration file is loaded before any dependency and
# is restricted to this project.

# General application configuration
import Config

config :broker,
  ecto_repos: [Broker.Repo],
  generators: [timestamp_type: :utc_datetime]

# Configure the endpoint
config :broker, BrokerWeb.Endpoint,
  url: [host: "localhost"],
  adapter: Bandit.PhoenixAdapter,
  render_errors: [
    formats: [json: BrokerWeb.ErrorJSON],
    layout: false
  ],
  pubsub_server: Broker.PubSub,
  live_view: [signing_salt: "Lwk7EWmF"]

# Configure Elixir's Logger
config :logger, :default_formatter,
  format: "$time $metadata[$level] $message\n",
  metadata: [:request_id]

# Use Jason for JSON parsing in Phoenix
config :phoenix, :json_library, Jason

# Configure Hammer for rate limiting
config :hammer,
  backend:
    {Hammer.Backend.Redis,
     [
       # 2 hours
       expiry_ms: 60_000 * 60 * 2,
       redix_config: [host: "127.0.0.1", port: 6379]
     ]}

# Configure ExAws for S3
config :ex_aws,
  access_key_id: [{:system, "AWS_ACCESS_KEY_ID"}, :instance_role],
  secret_access_key: [{:system, "AWS_SECRET_ACCESS_KEY"}, :instance_role],
  region: "us-east-1"

config :ex_aws, :s3,
  scheme: "https://",
  host: "s3.amazonaws.com",
  region: "us-east-1"

# Firebase Cloud Messaging
config :broker,
  firebase_project_id: "messenger-bd0f5",
  # JWT secret: In production, set JWT_SECRET env var. Dev fallback is in dev.exs.
  jwt_secret: System.get_env("JWT_SECRET"),
  # HMAC secret for phone number hashing (prevents rainbow table attacks)
  phone_hash_secret: System.get_env("PHONE_HASH_SECRET"),
  # Email address used as the "from" in OTP emails
  otp_sender_email: System.get_env("OTP_SENDER_EMAIL") || "aura.chat.verify@gmail.com"

# Gmail SMTP configuration for sending OTP emails
config :broker, Broker.Mailer,
  adapter: Swoosh.Adapters.SMTP,
  relay: "smtp.gmail.com",
  port: 587,
  username: System.get_env("GMAIL_USERNAME") || "aura.chat.verify@gmail.com",
  password: System.get_env("GMAIL_APP_PASSWORD") || "oegifwxgpwfdcyzv",
  tls: :always,
  auth: :always,
  no_mx_lookups: true,
  tls_options: [
    verify: :verify_none
  ]

# Import environment specific config. This must remain at the bottom
# of this file so it overrides the configuration defined above.
import_config "#{config_env()}.exs"
