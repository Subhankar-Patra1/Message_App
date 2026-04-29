defmodule BrokerWeb.Router do
  use BrokerWeb, :router

  pipeline :api do
    plug :accepts, ["json"]
  end

  pipeline :api_auth do
    plug BrokerWeb.Plugs.Auth
  end

  scope "/api/v1", BrokerWeb do
    pipe_through [:api]

    post "/auth/identify", AuthController, :identify
    post "/auth/google", AuthController, :google_auth
    post "/auth/verify_password", AuthController, :verify_password
    post "/auth/verify_otp", AuthController, :verify_otp
    post "/auth/refresh", AuthController, :refresh
    post "/auth/resend_otp", AuthController, :resend_otp
    post "/auth/send_phone_otp", AuthController, :send_phone_otp
    post "/auth/forgot_password", AuthController, :forgot_password
    post "/auth/reset_password", AuthController, :reset_password
    post "/qr/generate", AuthController, :generate_qr
    get "/qr/poll/:token", AuthController, :poll_qr
    get "/healthz", HealthController, :check
    get "/account/check_username", UserController, :check_username
  end

  scope "/api/v1", BrokerWeb do
    pipe_through [:api, :api_auth]

    post "/qr/validate", AuthController, :validate_qr
    post "/auth/logout", AuthController, :logout
    post "/auth/logout_all", AuthController, :logout_all
    post "/keys/register", KeyController, :register
    put "/keys/signed_pre_key", KeyController, :rotate_signed_pre_key
    get "/pre_keys", KeyController, :fetch_bundle

    # Group Chat Endpoints
    post "/groups", GroupController, :create
    get "/groups/:id", GroupController, :show
    post "/groups/:id/members", GroupController, :add_member
    delete "/groups/:id/members/:user_id", GroupController, :remove_member

    # Device Management
    put "/devices/push_token", DeviceController, :update_push_token

    # Account
    get "/account/profile", UserController, :show_profile
    put "/account/profile", UserController, :update_profile
    put "/account/username", UserController, :set_username
    delete "/account", AuthController, :delete_account
    
    # Username Key Lookup
    get "/keys/fetch_by_username/:username", KeyController, :fetch_by_username

    # Phone/Email Key Lookup
    get "/keys/fetch_by_identifier", KeyController, :fetch_by_identifier
    
    # User Status
    get "/account/status/:user_id", UserController, :get_status

    # Public Profile (resolve UUID → display name)
    get "/account/public_profile/:user_id", UserController, :get_public_profile
  end

  # Enable LiveDashboard in development
  if Application.compile_env(:broker, :dev_routes) do
    # If you want to use the LiveDashboard in production, you should put
    # it behind authentication and allow only admins to access it.
    # If your application does not have an admins-only section yet,
    # you can use Plug.BasicAuth to set up some basic authentication
    # as long as you are also using SSL (which you should anyway).
    import Phoenix.LiveDashboard.Router

    scope "/dev" do
      pipe_through [:fetch_session, :protect_from_forgery]

      live_dashboard "/dashboard", metrics: BrokerWeb.Telemetry
    end
  end
end
