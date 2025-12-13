terraform {
  required_providers {
    cloudflare = {
      source  = "cloudflare/cloudflare"
      version = "~> 5.0"
    }
    random = {
      source = "hashicorp/random"
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

resource "random_id" "tunnel_secret" {
  byte_length = 32
}

resource "cloudflare_zero_trust_tunnel_cloudflared" "city_explorer_tunnel" {
  account_id = var.account_id
  name       = "city-explorer-tunnel"
  secret     = random_id.tunnel_secret.b64_std 
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

resource "cloudflare_dns_record" "city_explorer_dns" {
  zone_id = var.zone_id
  name    = "city-explorer-api"
  content = "${cloudflare_zero_trust_tunnel_cloudflared.city_explorer_tunnel.id}.cfargotunnel.com"
  type    = "CNAME"
  proxied = true
}

output "tunnel_token" {
  sensitive = true
  value = base64encode(jsonencode({
    "a" = var.account_id,
    "t" = cloudflare_zero_trust_tunnel_cloudflared.city_explorer_tunnel.id,
    "s" = random_id.tunnel_secret.b64_std
  }))
}

# terraform apply
# terraform output -raw tunnel_token
# docker-compose up -d --force-recreate --build cloudflared
