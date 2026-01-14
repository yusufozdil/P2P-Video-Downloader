docker-compose down
docker-compose up --build

docker logs -f cse471_term_project-peer3-1

docker exec cse471_term_project-peer1-1 tc qdisc add dev eth0 root netem delay 600ms

*   `tc`: Traffic Control
*   `qdisc`: Queueing Discipline
*   `netem`: Network Emulator
*   `delay 600ms`: Her paketi 600ms beklet.

docker exec cse471_term_project-peer1-1 tc qdisc del dev eth0 root

