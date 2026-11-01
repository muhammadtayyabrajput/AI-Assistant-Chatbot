# Deploying FintechAssist (free): Oracle Cloud VM + Vercel

```
Browser ──► Vercel (Next.js frontend)
   │
   └──► https://YOURNAME.duckdns.org  ──► Caddy (HTTPS) ──► auth / banking / chat services
                                                              └─► MySQL + pgvector (private)
```
Everything in the backend runs on one free VM with `docker-compose.prod.yml`.

## 0. Before you start
- Rotate secrets: create a **new** OpenRouter key for production (the dev key was shared in chat).
- Put the project on GitHub (private is fine). `.env` and `.env.prod` are git-ignored.
  ```powershell
  cd "D:\AI Assistant Chatbot"
  git init
  git add .
  git status          # check that no .env file is listed
  git commit -m "FintechAssist AI"
  git branch -M main
  git remote add origin https://github.com/YOUR_USER/fintech-assist.git
  git push -u origin main
  ```

## 1. Create the free VM (Oracle Cloud)
1. Sign up at cloud.oracle.com (a card is needed for verification only). Pick a home region and keep it.
2. **Compute -> Instances -> Create instance**
   - Image: **Ubuntu 22.04** (or 24.04)
   - Shape: **VM.Standard.A1.Flex** (Ampere ARM) with **4 OCPU / 24 GB** (the Always Free maximum).
     If it says "Out of capacity", retry later or try another availability domain.
   - Networking: keep the public IPv4 address enabled.
   - Add your SSH public key (or let Oracle generate one and download it).
3. Open the web ports: **Networking -> Virtual cloud networks -> your VCN -> Security lists -> Default -> Add ingress rules**
   - Source `0.0.0.0/0`, TCP, destination ports `80` and `443` (two rules or one rule `80,443`).
4. Note the VM's **public IP**.

## 2. Free domain name (needed for HTTPS)
1. Go to duckdns.org, sign in, create a subdomain, e.g. `myfintech`, and set its IP to the VM's public IP.
2. Your API address is `myfintech.duckdns.org`.

## 3. Prepare the VM
```bash
ssh ubuntu@VM_PUBLIC_IP

# Ubuntu images on Oracle block ports at the OS level too: open 80/443
sudo iptables -I INPUT 6 -m state --state NEW -p tcp --dport 80 -j ACCEPT
sudo iptables -I INPUT 6 -m state --state NEW -p tcp --dport 443 -j ACCEPT
sudo iptables -I INPUT 6 -m state --state NEW -p udp --dport 443 -j ACCEPT
sudo apt-get install -y iptables-persistent && sudo netfilter-persistent save

# Docker
curl -fsSL https://get.docker.com | sudo sh
sudo usermod -aG docker $USER
exit    # log in again so the docker group applies
```

## 4. Deploy the backend
```bash
ssh ubuntu@VM_PUBLIC_IP
git clone https://github.com/YOUR_USER/fintech-assist.git
cd fintech-assist
cp .env.prod.example .env.prod
nano .env.prod
```
Fill in `.env.prod`:
| Variable | Value |
|---|---|
| `DOMAIN` | `myfintech.duckdns.org` |
| `CORS_ALLOWED_ORIGIN` | your Vercel URL (set a placeholder now, fix it in step 6) |
| `JWT_SECRET` | output of `openssl rand -base64 48` |
| `MYSQL_ROOT_PASSWORD`, `POSTGRES_PASSWORD` | long random passwords |
| `OPENROUTER_API_KEY` | the new production key |

Start everything (first build takes several minutes on the VM):
```bash
docker compose --env-file .env.prod -f docker-compose.prod.yml up -d --build
docker compose --env-file .env.prod -f docker-compose.prod.yml ps
docker compose --env-file .env.prod -f docker-compose.prod.yml logs -f chat-service   # wait for "Started ChatServiceApplication"
```
Check from your own computer: opening `https://myfintech.duckdns.org` should show `FintechAssist API is running`.
(Caddy fetches the certificate on first request; allow a minute. DNS must already point to the VM.)

## 5. Deploy the frontend on Vercel
1. vercel.com -> **Add New -> Project** -> import the GitHub repo.
2. **Root Directory:** `frontend`. Framework: Next.js (auto-detected).
3. **Environment variables** (all three point to the same address because Caddy routes by path):
   ```
   NEXT_PUBLIC_AUTH_URL     = https://myfintech.duckdns.org
   NEXT_PUBLIC_BANKING_URL  = https://myfintech.duckdns.org
   NEXT_PUBLIC_CHAT_URL     = https://myfintech.duckdns.org
   ```
4. Deploy. Note the URL, e.g. `https://fintech-assist.vercel.app`.

## 6. Connect the two (CORS)
Put the exact Vercel URL (no trailing slash) in `CORS_ALLOWED_ORIGIN` on the VM, then recreate the services:
```bash
nano .env.prod
docker compose --env-file .env.prod -f docker-compose.prod.yml up -d
```
Open the Vercel URL, sign up, and chat.
Note: Vercel *preview* URLs have different addresses and will be blocked by CORS, which is intended. Use the production URL.

## Password-reset emails
With no SMTP configured, reset links are only written to the auth-service log:
`docker compose --env-file .env.prod -f docker-compose.prod.yml logs auth-service | grep "password reset"`
To email them, create a free SMTP account (Brevo, Resend, ...), uncomment the `SPRING_MAIL_*` lines in `docker-compose.prod.yml` under `auth-service`, fill them in, and run `up -d` again.

## Updating later
```bash
cd fintech-assist && git pull
docker compose --env-file .env.prod -f docker-compose.prod.yml up -d --build
```
(Vercel redeploys the frontend automatically on each push.)

## Good to know
- Banking data (accounts, cards, transactions) is generated in memory, so it resets when `banking-service` restarts. Users, chats and FAQ embeddings live in the databases and survive restarts.
- Back up the databases: `docker compose --env-file .env.prod -f docker-compose.prod.yml exec mysql mysqldump -uroot -p"$MYSQL_ROOT_PASSWORD" --all-databases > backup.sql`
- Oracle may reclaim idle Always Free instances. Keep the VM in use (it is, if people use the app) and keep backups.
- Logout clears the token in the browser, but tokens are stateless JWTs and stay valid until they expire (24h).
