import logging
from api_client import TrendifyClient
from config import Config
from generator import TrafficGenerator

def main():
    logging.basicConfig(level=logging.INFO,format="%(asctime)s %(levelname)s %(message)s"); config=Config(); config.validate()
    client=TrendifyClient(config.api_url,config.simulator_key); client.login(config.email,config.password)
    logging.info("Starting %s synthetic traffic at %.1f events/second",config.pattern,config.events_per_second)
    TrafficGenerator(client,config.seed,config.pattern).run(config.events_per_second)

if __name__=="__main__": main()
