#!/bin/bash

function usage {

 echo "Usage: $0 { -i|--install | -r|--uninstall | -u|--upgrade | -p|--package -c|--clean }"
 echo ""
 echo "Options:"
 echo "  -i, --install    Install the chart"
 echo "  -r, --uninstall  Uninstall the chart"
 echo "  -u, --upgrade    Upgrade the chart"
 echo "  -p, --package    Package the chart into a tarball"
 echo "  -d, --redeploy   Removes existing, re-packages,re-installs, and cleans up tarball"
 echo "  -c, --clean      Removes the chart tarball"

 echo
 echo "Overview"
 echo
 echo "Build Chart (tarball):"
 echo -e "\e[92m helm package ./pre-auth-shim\e[0m"

 echo "Install Chart:"
 echo -e "\e[92m helm install pre-auth-shim ./pre-auth-shim-0.1.0.tgz --namespace pre-auth-shim --create-namespace \e[0m"

 echo "Update Chart:"
 echo -e "\e[92m helm upgrade --install pre-auth-shim ./pre-auth-shim-0.1.0.tgz --namespace pre-auth-shim \e[0m"

 echo "Delete Chart:"
 echo -e "\e[92m helm uninstall pre-auth-shim -n pre-auth-shim\e[0m"
 echo

 echo -e " \e[93mAuth Control Dashboard\e[0m : \e[92mhttps://auth.xxx.local/ui\e[0m"
 echo -e " \e[93mProtected Acccess URL\e[0m  : \e[92mhttps://protected.xxx.local/ui\e[0m"
 echo
}

package-chart() {
 echo -e "\e[92mPackaging the chart...\e[0m"
 helm package ./pre-auth-shim
}

install-chart() {
 echo -e "\e[92mInstalling the chart...\e[0m"
 helm install pre-auth-shim ./pre-auth-shim-0.1.0.tgz --namespace pre-auth-shim --create-namespace
}

upgrade-chart() {
 echo -e "\e[92mUpgrading the chart...\e[0m"
 helm upgrade --install pre-auth-shim ./pre-auth-shim-0.1.0.tgz --namespace pre-auth-shim
}

remove-chart() {
 echo -e "\e[92mUninstalling the chart...\e[0m"
 helm uninstall pre-auth-shim -n pre-auth-shim
 kubectl delete namespace pre-auth-shim
}

clean-chart() {
 echo -e "\e[92mRemoving chart tarballs...\e[0m"
 rm -f pre-auth-shim-*.tgz 2>/dev/null
 echo "Clean complete."
}

deploy-chart() {

 echo "Fully redeploying Chart..."
 remove-chart
 package-chart
 install-chart
 clean-chart

 echo "Deployment complete!"
}

case "$1" in
 -i | --install)
  install-chart
  ;;
 -r | --uninstall | --remove)
  remove-chart
  ;;
 -u | --upgrade)
  upgrade-chart
  ;;
 -c | --clean)
  clean-chart
  ;;
 -d | --redeploy)
  deploy-chart
  ;;
 -p | --package)
  package-chart
  ;;
 -h | --help)
  usage
  ;;
 *)
  usage
  ;;
esac
