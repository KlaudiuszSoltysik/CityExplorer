terraform {
  required_providers {
    cloudflare = {
      source  = "cloudflare/cloudflare"
      version = "~> 4.0"
    }
  }
}

variable "cloudflare_api_token" {
  description = "API Token"
  sensitive   = true
}

variable "account_id" {
  description = "Account ID"
}

variable "zone_id" {
  description = "Zone ID"
}

provider "cloudflare" {
  api_token = var.cloudflare_api_token
}

resource "cloudflare_zero_trust_tunnel_cloudflared" "city_explorer_tunnel" {
  account_id = var.account_id
  name       = "city-explorer-tunnel"
  secret     = base64sha256(var.cloudflare_api_token)
}

resource "cloudflare_zero_trust_tunnel_cloudflared_config" "city_explorer_config" {
  account_id = var.account_id
  tunnel_id  = cloudflare_zero_trust_tunnel_cloudflared.city_explorer_tunnel.id

  config {
    ingress_rule {
      hostname = "city-explorer-api.260824.xyz" 
      service  = "http://backend:8080"
    }
    ingress_rule {
      service = "http_status:404"
    }
  }
}

resource "cloudflare_record" "city_explorer_dns" {
  zone_id = var.zone_id
  name    = "city-explorer-api" 
  content = "${cloudflare_zero_trust_tunnel_cloudflared.city_explorer_tunnel.id}.cfargotunnel.com"
  type    = "CNAME"
  proxied = true
}

output "tunnel_token" {
  value     = cloudflare_zero_trust_tunnel_cloudflared.city_explorer_tunnel.tunnel_token
  sensitive = true
}