defmodule BrokerWeb.MediaController do
  use BrokerWeb, :controller
  require Logger

  # Action fallbacks, auth plugs, etc.
  action_fallback BrokerWeb.FallbackController

  # Ensure only authenticated users can request URLs
  plug BrokerWeb.Plugs.Auth

  @bucket System.get_env("AWS_S3_BUCKET") || "secure-chats"
  # 30 minutes
  @upload_expiry 1800
  # 1 hour
  @download_expiry 3600

  def upload_url(conn, %{"file_size" => _size, "mime_type" => mime} = params) do
    current_user_id = conn.assigns[:current_user_id]
    recipient_id = params["recipient_id"]

    # Simple scope validation: In a real system, we'd check if the user is in the room.
    # Here, we just require the recipient_id to be provided.
    if is_nil(recipient_id) do
      conn |> put_status(400) |> json(%{error: "recipient_id is required"})
    else
      # 1. Generate a random unique key for S3
      s3_key = "uploads/#{Ecto.UUID.generate()}"

      # 2. Generate pre-signed PUT URL
      config = ExAws.Config.new(:s3)

      {:ok, url} =
        ExAws.S3.presigned_url(config, :put, @bucket, s3_key,
          expires_in: @upload_expiry,
          query_params: [{"Content-Type", mime}]
        )

      Logger.info(
        "Media Upload Presigned - User: #{current_user_id}, Recipient: #{recipient_id}, S3 Key: #{s3_key}"
      )

      json(conn, %{
        upload_url: url,
        s3_key: s3_key,
        # 50MB limit example
        max_size: 50 * 1024 * 1024,
        expires_at: System.system_time(:second) + @upload_expiry
      })
    end
  end

  def download_url(conn, %{"s3_key" => s3_key}) do
    current_user_id = conn.assigns[:current_user_id]

    # In production, verify the user has access to this conversation/file!
    # For now, we trust the s3_key if the user is authenticated.

    config = ExAws.Config.new(:s3)

    {:ok, url} =
      ExAws.S3.presigned_url(config, :get, @bucket, s3_key, expires_in: @download_expiry)

    Logger.info("Media Download Presigned - User: #{current_user_id}, S3 Key: #{s3_key}")

    json(conn, %{
      download_url: url,
      expires_at: System.system_time(:second) + @download_expiry
    })
  end
end
