
.PHONY=help

help:  ## This help
	@awk 'BEGIN {FS = ":.*?## "} /^[a-zA-Z_-]+:.*?## / {printf "\033[36m%-20s\033[0m %s\n", $$1, $$2}' $(MAKEFILE_LIST) | sort

###
# Run section
###
clean: ## Clean our project build tmp files
	fvm flutter clean && fvm flutter pub get
run: ## Run any app with flavor name. e.g. make run base
	fvm flutter run 
	
build-native: ## Build an native library
	sh scripts/build_android_aar.sh

deploy: ## Deploy project to firebase hosting
	make clean
	make build-native
	flutter pub publish

###
# Dependencies section
###
dependencies: ## Install all dependencies

	fvm flutter pub get


###
# Tests section
###
test: ## Run all tests
	fvm flutter test
