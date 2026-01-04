terraform {
  required_providers {
    cloudflare = {
      source  = "cloudflare/cloudflare"
      version = "~> 4.40.0"
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

resource "cloudflare_tunnel" "city_explorer_tunnel" {
  account_id = var.account_id
  name       = "city-explorer-tunnel"
  secret     = random_id.tunnel_secret.b64_std
}

resource "cloudflare_tunnel_config" "city_explorer_config" {
  account_id = var.account_id
  tunnel_id  = cloudflare_tunnel.city_explorer_tunnel.id

  config {
    ingress_rule {
      hostname = "city-explorer-api.260824.xyz"
      service  = "http://api:8080"
    }
    ingress_rule {
      hostname = "city-explorer-portainer.260824.xyz"
      service  = "http://portainer:9000"
    }
    ingress_rule {
      hostname = "city-explorer-grafana.260824.xyz"
      service  = "http://grafana:3000"
    }
    ingress_rule {
      service = "http_status:404"
    }
  }
}

resource "cloudflare_record" "city_explorer_dns" {
  zone_id = var.zone_id
  name    = "city-explorer-api"
  value   = "${cloudflare_tunnel.city_explorer_tunnel.id}.cfargotunnel.com"
  type    = "CNAME"
  proxied = true
}

resource "cloudflare_record" "glances_dns" {
  zone_id = var.zone_id
  name    = "city-explorer-portainer"
  value   = "${cloudflare_tunnel.city_explorer_tunnel.id}.cfargotunnel.com"
  type    = "CNAME"
  proxied = true
}

resource "cloudflare_access_application" "glances_access" {
  account_id       = var.account_id
  name             = "City Explorer Monitoring"
  domain           = "city-explorer-portainer.260824.xyz"
  type             = "self_hosted"
  session_duration = "24h"
}

resource "cloudflare_access_policy" "glances_policy" {
  account_id     = var.account_id
  application_id = cloudflare_access_application.glances_access.id
  name           = "Allow Only Me"
  precedence     = "1"
  decision       = "allow"

  include {
    email = ["klaudiusz.s1405@gmail.com"]
  }
}

resource "cloudflare_record" "grafana_dns" {
  zone_id = var.zone_id
  name    = "city-explorer-grafana"
  value   = "${cloudflare_tunnel.city_explorer_tunnel.id}.cfargotunnel.com"
  type    = "CNAME"
  proxied = true
}

resource "cloudflare_access_application" "grafana_access" {
  account_id       = var.account_id
  name             = "City Explorer Grafana"
  domain           = "city-explorer-grafana.260824.xyz"
  type             = "self_hosted"
  session_duration = "24h"
}

resource "cloudflare_access_policy" "grafana_policy" {
  account_id     = var.account_id
  application_id = cloudflare_access_application.grafana_access.id
  name           = "Allow Only Me"
  precedence     = "1"
  decision       = "allow"

  include {
    email = ["klaudiusz.s1405@gmail.com"]
  }
}

output "tunnel_token" {
  sensitive = true
  value = base64encode(jsonencode({
    "a" = var.account_id,
    "t" = cloudflare_tunnel.city_explorer_tunnel.id,
    "s" = random_id.tunnel_secret.b64_std
  }))
}