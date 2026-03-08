const mineflayer = require('mineflayer')

const HOST = process.env.MC_HOST || '127.0.0.1'
const PORT = Number(process.env.MC_PORT || 25565)
const TIMEOUT_MS = Number(process.env.BOT_TIMEOUT_MS || 20000)
const CONNECT_TIMEOUT_MS = Number(process.env.BOT_CONNECT_TIMEOUT_MS || 45000)
const BOT_NAME = process.env.BOT_LEADER || 'LeaderBot2'
const MC_VERSION = process.env.BOT_MC_VERSION || '1.21.11'
const ALLOW_ITEMSADDER_RESOURCEPACK = process.env.BOT_ALLOW_ITEMSADDER_RESOURCEPACK === '1'

function sleep(ms) {
  return new Promise((resolve) => setTimeout(resolve, ms))
}

function nowTag() {
  const stamp = Date.now().toString(36).toUpperCase()
  return {
    name: `M5${stamp.slice(-8)}`,
    tag: `M${stamp.slice(-2)}`
  }
}

function randomSpawnPoint() {
  const base = 4000
  const spread = 14000
  const x = base + Math.floor(Math.random() * spread)
  const z = base + Math.floor(Math.random() * spread)
  return { x, z }
}

function extractUuid(line) {
  if (!line) {
    return null
  }
  const match = String(line).match(/[0-9a-f]{8}-[0-9a-f]{4}-[1-5][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}/i)
  return match ? match[0] : null
}

class BotHarness {
  constructor(username) {
    this.username = username
    this.bot = null
    this.logs = []
    this._messageHandler = null
    this._resourcePackSeen = false
    this._resourcePackUrl = null
  }

  async connect() {
    this.bot = mineflayer.createBot({
      host: HOST,
      port: PORT,
      username: this.username,
      auth: 'offline',
      version: MC_VERSION || undefined,
      hideErrors: false
    })

    const onLine = (lineRaw) => {
      const line = String(lineRaw || '')
      if (!line) {
        return
      }
      this.logs.push(line)
      process.stdout.write(`[${this.username}] ${line}\n`)
    }
    this._messageHandler = onLine

    this.bot.on('messagestr', onLine)
    this.bot.on('message', (jsonMsg) => {
      if (!jsonMsg) {
        return
      }
      const line = typeof jsonMsg.toString === 'function' ? jsonMsg.toString() : String(jsonMsg)
      onLine(line)
    })
    this.bot.on('login', () => {
      process.stdout.write(`[${this.username}] login ok\n`)
    })
    this.bot.on('spawn', () => {
      process.stdout.write(`[${this.username}] spawn ok\n`)
    })
    this.bot.on('resourcePack', (url, hash) => {
      this._resourcePackSeen = true
      this._resourcePackUrl = String(url || '')
      process.stdout.write(`[${this.username}] resource-pack request ${url} (${hash || 'no-hash'})\n`)
      if (!ALLOW_ITEMSADDER_RESOURCEPACK) {
        process.stdout.write(
          `[${this.username}] resource-pack disabled for bot test run; start server with ENABLE_ITEMSADDER=0 or set BOT_ALLOW_ITEMSADDER_RESOURCEPACK=1\n`
        )
        this.bot.quit('resource-pack-disabled-for-bot-tests')
        return
      }
      try {
        this.bot.acceptResourcePack()
      } catch (err) {
        process.stdout.write(`[${this.username}] resource-pack accept failed: ${err.message}\n`)
      }
    })

    return new Promise((resolve, reject) => {
      let resolved = false
      const timer = setTimeout(() => {
        cleanup()
        if (this._resourcePackSeen) {
          reject(
            new Error(
              `connect timeout after ${CONNECT_TIMEOUT_MS}ms (resource-pack seen: ${this._resourcePackUrl || 'unknown'})`
            )
          )
          return
        }
        reject(new Error(`connect timeout after ${CONNECT_TIMEOUT_MS}ms`))
      }, CONNECT_TIMEOUT_MS)

      const onLogin = async () => {
        await sleep(1200)
        if (!resolved) {
          resolved = true
          cleanup()
          resolve()
        }
      }
      const onSpawn = () => {
        resolved = true
        cleanup()
        resolve()
      }
      const onError = (err) => {
        cleanup()
        reject(err)
      }
      const onKick = (reason) => {
        cleanup()
        reject(new Error(`kicked: ${JSON.stringify(reason)}`))
      }
      const onEnd = (reason) => {
        cleanup()
        reject(new Error(`connection ended before ready: ${String(reason || 'unknown')}`))
      }
      const cleanup = () => {
        clearTimeout(timer)
        this.bot.off('login', onLogin)
        this.bot.off('spawn', onSpawn)
        this.bot.off('error', onError)
        this.bot.off('kicked', onKick)
        this.bot.off('end', onEnd)
      }
      this.bot.once('login', onLogin)
      this.bot.once('spawn', onSpawn)
      this.bot.once('error', onError)
      this.bot.once('kicked', onKick)
      this.bot.once('end', onEnd)
    })
  }

  async waitFor(matchers, timeoutMs = TIMEOUT_MS, startIndex = this.logs.length) {
    const regexes = (Array.isArray(matchers) ? matchers : [matchers]).map((matcher) =>
      matcher instanceof RegExp ? matcher : new RegExp(String(matcher), 'i')
    )
    return new Promise((resolve, reject) => {
      const checkLine = (lineRaw) => {
        const line = String(lineRaw || '')
        for (const re of regexes) {
          if (re.test(line)) {
            cleanup()
            resolve({ line, re })
            return true
          }
        }
        return false
      }
      const onMessageStr = (msg) => {
        checkLine(msg)
      }
      const onMessageJson = (jsonMsg) => {
        if (!jsonMsg) {
          return
        }
        const line = typeof jsonMsg.toString === 'function' ? jsonMsg.toString() : String(jsonMsg)
        checkLine(line)
      }
      const cleanup = () => {
        clearTimeout(timer)
        this.bot.off('messagestr', onMessageStr)
        this.bot.off('message', onMessageJson)
      }
      const timer = setTimeout(() => {
        cleanup()
        reject(new Error(`timeout waiting for: ${regexes.map((it) => it.toString()).join(', ')}`))
      }, timeoutMs)

      for (const line of this.logs.slice(startIndex)) {
        if (checkLine(line)) {
          return
        }
      }
      this.bot.on('messagestr', onMessageStr)
      this.bot.on('message', onMessageJson)
    })
  }

  async runCommand(command, expected, forbidden = []) {
    const expectedMatchers = Array.isArray(expected) ? expected : [expected]
    const forbiddenMatchers = (Array.isArray(forbidden) ? forbidden : [forbidden])
      .filter(Boolean)
      .map((matcher) => (matcher instanceof RegExp ? matcher : new RegExp(String(matcher), 'i')))

    const startIndex = this.logs.length
    const waiter = this.waitFor([...expectedMatchers, ...forbiddenMatchers], TIMEOUT_MS, startIndex)
    this.bot.chat(command)
    const hit = await waiter

    for (const re of forbiddenMatchers) {
      if (re.test(hit.line)) {
        throw new Error(`forbidden response for command '${command}': ${hit.line}`)
      }
    }

    const matchedExpected = expectedMatchers.some((matcher) => {
      const re = matcher instanceof RegExp ? matcher : new RegExp(String(matcher), 'i')
      return re.test(hit.line)
    })
    if (!matchedExpected) {
      throw new Error(`unexpected response for command '${command}': ${hit.line}`)
    }
    return hit.line
  }

  async runCommandBestEffort(command, expected, timeoutMs = TIMEOUT_MS) {
    try {
      const startIndex = this.logs.length
      const waiter = this.waitFor(expected, timeoutMs, startIndex)
      this.bot.chat(command)
      const hit = await waiter
      return hit.line
    } catch (_ignored) {
      return null
    }
  }

  async relocateForClaim(attempt) {
    const point = randomSpawnPoint()
    process.stdout.write(`[${this.username}] relocating for claim attempt=${attempt} x=${point.x} z=${point.z}\n`)
    this.bot.chat(`/tp ${point.x} 90 ${point.z}`)
    await sleep(900)
  }

  async createCityWithRetries(cityName, cityTag, hardFailure, maxAttempts = 6, bannerSpec = '', armorSpec = '') {
    const extra = [bannerSpec, armorSpec].filter(Boolean).join(' ')
    for (let attempt = 1; attempt <= maxAttempts; attempt += 1) {
      await this.relocateForClaim(attempt)
      await this.runCommand(
        `/city create ${cityName} ${cityTag}${extra ? ` ${extra}` : ''}`,
        /Creazione città in corso|Città creata|Creazione claim fallita|Nome o tag già in uso|Operazione fallita/i,
        hardFailure
      )
      const outcome = await this.waitFor(
        [/Città creata/i, /Nome o tag già in uso/i, /Creazione claim fallita:/i, /Operazione fallita/i],
        TIMEOUT_MS
      )
      if (/Città creata/i.test(outcome.line) || /Nome o tag già in uso/i.test(outcome.line)) {
        return outcome
      }
      if (/Operazione fallita/i.test(outcome.line)) {
        throw new Error(`city create failed: ${outcome.line}`)
      }
      if (/region-already-claimed/i.test(outcome.line)) {
        process.stdout.write(`[${this.username}] claim overlap detected, retrying create\n`)
        continue
      }
      throw new Error(`city create failed with unrecoverable response: ${outcome.line}`)
    }
    throw new Error(`city create failed after ${maxAttempts} attempts (claim overlaps)`)
  }

  async close() {
    if (!this.bot) {
      return
    }
    if (this._messageHandler) {
      this.bot.off('messagestr', this._messageHandler)
    }
    this.bot.quit('test-end')
    await sleep(500)
  }
}

async function main() {
  const identity = nowTag()
  const cityName = identity.name
  const cityTag = identity.tag
  process.stdout.write(`[BOT-M5] using cityName=${cityName} tag=${cityTag}\n`)

  const hardFailure = /Operazione fallita|Unhandled exception|Command exception|java\.lang\./i
  const bannerSpec = process.env.BOT_BANNER_SPEC || 'red_banner'
  const armorSpec = process.env.BOT_ARMOR_SPEC || 'NETHERITE'
  const leader = new BotHarness(BOT_NAME)
  await leader.connect()
  await sleep(1200)

  const currentInfo = await leader.runCommandBestEffort('/city info', [/Città non trovata/i, /Tier:/i], 12000)
  if (currentInfo && /Tier:/i.test(currentInfo)) {
    const refs = [...currentInfo.matchAll(/\[([A-Za-z0-9_:-]{2,16})\]/g)].map((it) => it[1]).filter((it) => it.toLowerCase() !== 'cittaexp')
    const currentRef = refs.length > 0 ? refs[refs.length - 1] : null
    if (currentRef) {
      process.stdout.write(`[BOT-M5] pre-clean detected city ref=${currentRef}\n`)
      await leader.runCommandBestEffort(`/cittaexp staff city delete ${currentRef} bot-m5-preclean`, [/Staff delete|DELETED|city-not-found/i], 12000)
      await sleep(700)
    }
  }

  await leader.createCityWithRetries(cityName, cityTag, hardFailure, 6, bannerSpec, armorSpec)
  await leader.runCommand('/city info', [new RegExp(cityTag, 'i'), /Tier:/i], hardFailure)
  await leader.runCommand('/city style show', [/Style città/i, /banner=/i], hardFailure)
  await leader.runCommand('/city style set banner blue_banner', [/Banner città aggiornato|Operazione fallita/i], hardFailure)
  await leader.runCommand('/city style set armor IRON', [/Armatura città aggiornata|Operazione fallita/i], hardFailure)
  await leader.runCommand('/city banner setlocation yellow_banner', [/Location stendardo salvata|Operazione fallita/i], hardFailure)
  await leader.runCommand('/city banner show', [/Stendardo città/i, /location=/i], hardFailure)
  await leader.runCommand('/city banner place', [/Render stendardo eseguito|Operazione fallita/i], hardFailure)
  await leader.runCommand('/city banner remove', [/Display stendardo rimossi|Operazione fallita/i], hardFailure)
  await leader.runCommand('/city banner clearlocation', [/Location stendardo rimossa|Operazione fallita/i], hardFailure)

  const createdUpgrade = await leader.runCommand('/city request upgrade test-m5-upgrade', /Ticket creato:|Ticket creato/i, hardFailure)
  const upgradeTicketId = extractUuid(createdUpgrade) || extractUuid((await leader.waitFor(/[0-9a-f-]{36}/i, 5000)).line)
  if (!upgradeTicketId) {
    throw new Error('unable to parse upgrade ticket id')
  }
  process.stdout.write(`[BOT-M5] upgradeTicketId=${upgradeTicketId}\n`)

  await leader.runCommand('/city request list', [new RegExp(upgradeTicketId, 'i'), /ticket/i], hardFailure)
  await leader.runCommand(`/cittaexp staff ticket view ${upgradeTicketId}`, [new RegExp(upgradeTicketId, 'i'), /PENDING/i], hardFailure)
  await leader.runCommand(`/cittaexp staff ticket approve ${upgradeTicketId} bot-m5-approve`, [/APPROVED|Ticket/i], hardFailure)
  await leader.runCommand(`/cittaexp staff ticket view ${upgradeTicketId}`, [new RegExp(upgradeTicketId, 'i'), /APPROVED/i], hardFailure)
  await leader.runCommand('/city info', [/Tier:\s*VILLAGGIO/i, new RegExp(cityTag, 'i')], hardFailure)
  await leader.runCommand('/city claimblocks shop', [/claim shop aperta|Apertura GUI|GUI/i, hardFailure], /Command exception|Unhandled exception|java\.lang\./i)
  await leader.runCommand('/city claimblocks buy 512', [/Acquisto completato|Fondi insufficienti|I Borghi non possono|Operazione fallita/i], /Command exception|Unhandled exception|java\.lang\./i)

  const createdDelete = await leader.runCommand('/city request delete test-m5-delete', /Ticket creato:|Ticket creato/i, hardFailure)
  const deleteTicketId = extractUuid(createdDelete) || extractUuid((await leader.waitFor(/[0-9a-f-]{36}/i, 5000)).line)
  if (!deleteTicketId) {
    throw new Error('unable to parse delete ticket id')
  }
  process.stdout.write(`[BOT-M5] deleteTicketId=${deleteTicketId}\n`)

  await leader.runCommand(`/cittaexp staff ticket reject ${deleteTicketId} bot-m5-reject`, [/REJECTED|Ticket/i], hardFailure)
  await leader.runCommand(`/cittaexp staff ticket view ${deleteTicketId}`, [new RegExp(deleteTicketId, 'i'), /REJECTED/i], hardFailure)

  const createdUpgrade2 = await leader.runCommand('/city request upgrade test-m5-upgrade-cancel', /Ticket creato:|Ticket creato/i, hardFailure)
  const cancelTicketId = extractUuid(createdUpgrade2) || extractUuid((await leader.waitFor(/[0-9a-f-]{36}/i, 5000)).line)
  if (!cancelTicketId) {
    throw new Error('unable to parse cancel ticket id')
  }
  process.stdout.write(`[BOT-M5] cancelTicketId=${cancelTicketId}\n`)

  await leader.runCommand(`/city request cancel ${cancelTicketId} bot-m5-cancel`, [/Ticket annullato|CANCELLED/i, new RegExp(cancelTicketId, 'i')], hardFailure)
  await leader.runCommand(`/cittaexp staff ticket view ${cancelTicketId}`, [new RegExp(cancelTicketId, 'i'), /CANCELLED/i], hardFailure)

  await leader.runCommand(`/cittaexp staff city delete ${cityTag} bot-m5-cleanup`, /Staff delete|DELETED/i, hardFailure)
  await leader.runCommand(`/city info ${cityTag}`, /Città non trovata/i, hardFailure)

  await leader.close()
  process.stdout.write('[BOT-M5] wave completed successfully\n')
}

main().catch((err) => {
  process.stderr.write(`[BOT-M5] FAILED: ${err.stack || err.message}\n`)
  process.exitCode = 1
})
