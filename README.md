# Minecraft-Discord bridge

Simple fabric mod that allows you to bridge together minecraft and discord. Doesn't require anything except fabric itself.

Uses webhooks to send messages disguised as the players, and a bot token to read messages to send back to the game.

## Config

The config goes in `config/discord-bridge.json` and contains these keys:

```
{
  "token": "insert-bot-token-here",
  "webhook_url": "insert-webhook-url-here",
  "channel_id": 0,
  "rename_channel_id": 0,
  "voice_channel_id": 0,
  "update_topic": false,
  "bot_whitelist": [],
  "rename_channel_format": "%d player(s) online",
  "no_players_topic_format": "Online!",
  "with_players_topic_format": "Online: %s",
  "avatar_url": "https://crafatar.com/renders/head/%s?overlay"
}
```

Replace the `token` field with the token of your bot and `channel_id` field with the ID of the channel you want to mirror the chat to.
