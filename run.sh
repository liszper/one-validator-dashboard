#!/bin/bash
echo "
                .-~~~~~~~~~-._       _.-~~~~~~~~~-.
            __.'              ~.   .~              \`.__
          .'//                  \\./                  \\\\\`.
        .'//                     |                     \\\\\`.
      .'// .-~\"\"\"\"\"\"\"~~~~-._     |     _,-~~~~\"\"\"\"\"\"\"~-. \\\\\`.
    .'//.-\"                 \`-.  |  .-'                 \"-.\\\\\`.
  .'//______.============-..   \\ | /   ..-============.______\\\\\`.
.'______________________________\\|/______________________________\`.
"
apt-get update && apt-get install git lolcat tmux leiningen -y && git clone https://github.com/liszper/one-validator-dashboard.git
curl -LO https://harmony.one/hmycli && mv hmycli hmy && chmod +x hmy
./hmy keys generate-bls-key
curl -LO https://raw.githubusercontent.com/harmony-one/harmony/master/scripts/node.sh && chmod a+x node.sh
cd one-validator-dashboard
./install.sh
