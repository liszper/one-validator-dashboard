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
apt-get update && apt-get install git lolcat tmux leiningen -y && git clone https://github.com/liszper/one-validator-dashboard.git && cd one-validator-dashboard && ./install.sh
