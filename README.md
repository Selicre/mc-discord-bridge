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

Field | Type | Description
--- | --- | ---
`token` | String | The bot token used to read Discord messages. **Required for the mod to function.** [Tutorial](https://discordpy.readthedocs.io/en/stable/discord.html)
`webhook_url` | String or null | The url for the webhook used to communicate as players. If left as `null`, will use the bot account to communicate instead. [Tutorial](https://support.discord.com/hc/en-us/articles/228383668-Intro-to-Webhooks)
`channel_id` | Long | The channel ID to read messages from. [Tutorial](https://support.discord.com/hc/en-us/articles/206346498-Where-can-I-find-my-User-Server-Message-ID-)
`rename_channel_id` | Long | The channel ID to rename to the current online player count.
`voice_channel_id` | Long | The channel ID to track the users in.
`update_topic` | Bool | Whether the bot should update the topic of the channel with the current online players.
`bot_whitelist` | Array&lt;Long&gt; | Bot IDs in this array will not be ignored.
`avatar_url` | String | The URL to use for the profile pics of users. By default, uses [Crafatar](https://crafatar.com/). Replaces `%s` with the UUID of the player.
`*_format` | String | Text to use. Replaces `%s` with an argument. Use `%%` to emit `%`.