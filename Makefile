SHELL := /bin/bash

NAMESPACE := mini-server-system

.PHONY: infra-up infra-down dev run down status

status:
	kubectl config current-context
	kubectl get nodes
	kubectl get ns $(NAMESPACE) >/dev/null 2>&1 || true

infra-up:
	kubectl apply -k deploy/kustomize/infra/overlays/local

infra-down:
	kubectl delete -k deploy/kustomize/infra/overlays/local --ignore-not-found=true

dev:
	# Continuous development loop (build -> deploy -> watch)
	skaffold dev --port-forward

run:
	skaffold run --port-forward

down:
	skaffold delete || true
	$(MAKE) infra-down
