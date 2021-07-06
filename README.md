# Minecraft-Discord bridge

Simple fabric mod that allows you to bridge together minecraft and discord. Doesn't require anything except fabric itself.

Uses webhooks to send messages disguised as the players, and a bot token to read messages to send back to the game.

## Config

The config goes in `config/discord-bridge.json` and contains these keys:

```
{
  "token": "insert-bot-token-here",
  "channel_id": 123123123123123,
  "webhook_url": "insert-webhook-url-here"
}
```

Replace the `token` field with the token of your bot and `channel_id` field with the ID of the channel you want to mirror the chat to.
