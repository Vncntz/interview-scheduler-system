package com.company.iss.client.config;

import com.company.iss.client.entity.Client;
import com.company.iss.client.repository.ClientRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@Profile("dev")
@ConditionalOnProperty(prefix = "iss.demo-data", name = "enabled", havingValue = "true")
@Order(10)
public class ClientDataLoader implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(ClientDataLoader.class);
    private static final int DEMO_CLIENT_COUNT = 50;

    private final ClientRepository clientRepository;

    public ClientDataLoader(ClientRepository clientRepository) {
        this.clientRepository = clientRepository;
    }

    @Override
    @Transactional
    public void run(String... args) {

        if (clientRepository.count() > 0) {
            log.info("[DEMO_DATA] Client seed skipped reason=CLIENT_DATA_PRESENT");
            return;
        }

        createClient("McDonald's Philippines", "Tanza, Cavite", "Juan Dela Cruz", "09171234567", "hr@mcdonalds.ph");
        createClient("Jollibee Foods Corporation", "Dasmarinas, Cavite", "Maria Santos", "09181234567", "hr@jollibee.com.ph");
        createClient("Chowking", "Imus, Cavite", "Carlos Reyes", "09191234567", "hr@chowking.com.ph");
        createClient("Mang Inasal", "Bacoor, Cavite", "Ana Cruz", "09201234567", "hr@manginasal.com.ph");
        createClient("Greenwich", "General Trias, Cavite", "Paolo Mendoza", "09211234567", "hr@greenwich.com.ph");

        createClient("SM Hypermarket", "Dasmarinas, Cavite", "Rica Gomez", "09221234567", "careers@sm.com");
        createClient("SM Supermarket", "Bacoor, Cavite", "Kevin Torres", "09231234567", "hr@smsupermarket.com");
        createClient("Puregold", "Imus, Cavite", "Liza Bautista", "09241234567", "recruitment@puregold.ph");
        createClient("Robinsons Supermarket", "Tagaytay, Cavite", "Nina Flores", "09251234567", "hr@robinsons.com.ph");
        createClient("WalterMart", "Trece Martires, Cavite", "Mark Rivera", "09261234567", "careers@waltermart.com.ph");

        createClient("Alfamart", "Naic, Cavite", "Grace Dizon", "09271234567", "hr@alfamart.com.ph");
        createClient("7-Eleven Philippines", "Kawit, Cavite", "Jerome Santos", "09281234567", "careers@7-eleven.com.ph");
        createClient("Mini Stop", "Rosario, Cavite", "Aileen Ramos", "09291234567", "jobs@ministop.ph");
        createClient("Lawson Philippines", "Bacoor, Cavite", "Patricia Gomez", "09301234567", "hr@lawson.ph");
        createClient("Uncle John's", "Imus, Cavite", "Ryan Cruz", "09311234567", "careers@unclejohns.ph");

        createClient("Concentrix", "Alabang, Muntinlupa", "Kenneth Ramos", "09321234567", "careers@concentrix.com");
        createClient("Teleperformance", "Makati City", "Monica Reyes", "09331234567", "recruitment@teleperformance.com");
        createClient("Foundever", "Pasig City", "Jason Lim", "09341234567", "careers@foundever.com");
        createClient("TaskUs", "Imus, Cavite", "Mika Torres", "09351234567", "jobs@taskus.com");
        createClient("Accenture", "Taguig City", "Angela Cruz", "09361234567", "careers@accenture.com");

        createClient("Amazon Operations", "Pasay City", "Daniel Flores", "09371234567", "careers@amazon.com");
        createClient("Lazada Philippines", "Taguig City", "Sophia Rivera", "09381234567", "jobs@lazada.com");
        createClient("Shopee Philippines", "BGC, Taguig", "Nicole Santos", "09391234567", "recruitment@shopee.ph");
        createClient("J&T Express", "Bacoor, Cavite", "Ronald Cruz", "09401234567", "hr@jtexpress.ph");
        createClient("LBC Express", "Paranaque City", "Melissa Ramos", "09411234567", "careers@lbc.com");

        createClient("DHL Philippines", "Pasay City", "Chris Lim", "09421234567", "jobs@dhl.com");
        createClient("Ninja Van", "Taguig City", "Vanessa Gomez", "09431234567", "careers@ninjavan.co");
        createClient("Grab Philippines", "Makati City", "Carlo Dizon", "09441234567", "recruitment@grab.com");
        createClient("Foodpanda Philippines", "Pasig City", "Pat Gomez", "09451234567", "jobs@foodpanda.ph");
        createClient("Angkas", "Makati City", "Jayson Cruz", "09461234567", "hr@angkas.com");

        createClient("SM Cinema", "Bacoor, Cavite", "Bea Torres", "09471234567", "hr@smcinema.com");
        createClient("Vista Mall", "Dasmarinas, Cavite", "Marlon Reyes", "09481234567", "careers@vistamall.com");
        createClient("Ayala Malls", "Alabang", "Cathy Santos", "09491234567", "jobs@ayalamalls.com");
        createClient("Enchanted Kingdom", "Laguna", "Princess Cruz", "09501234567", "careers@enchantedkingdom.ph");
        createClient("Okada Manila", "Paranaque", "Harold Lim", "09511234567", "hr@okadamanila.com");

        createClient("S&R Membership Shopping", "Bacoor, Cavite", "Ramon Flores", "09521234567", "careers@snrshopping.com");
        createClient("Landers Superstore", "Alabang", "Julia Gomez", "09531234567", "jobs@landers.ph");
        createClient("Wilcon Depot", "Dasmarinas, Cavite", "Peter Cruz", "09541234567", "hr@wilcon.com.ph");
        createClient("AllHome", "Imus, Cavite", "Michelle Torres", "09551234567", "careers@allhome.com.ph");
        createClient("Ace Hardware", "Bacoor, Cavite", "Noel Santos", "09561234567", "jobs@acehardware.ph");

        createClient("Mercury Drug", "Imus, Cavite", "Irene Flores", "09571234567", "hr@mercurydrug.com");
        createClient("Watsons Philippines", "Dasmarinas, Cavite", "Marvin Cruz", "09581234567", "careers@watsons.com.ph");
        createClient("Southstar Drug", "Bacoor, Cavite", "Tina Ramos", "09591234567", "jobs@southstardrug.com");
        createClient("Generika Drugstore", "General Trias, Cavite", "Louie Santos", "09601234567", "recruitment@generika.ph");
        createClient("The Generics Pharmacy", "Imus, Cavite", "Jessa Cruz", "09611234567", "hr@tgp.com.ph");

        createClient("Starbucks Philippines", "Taguig City", "Nicole Lim", "09621234567", "careers@starbucks.ph");
        createClient("Coffee Bean & Tea Leaf", "Makati City", "Patrick Gomez", "09631234567", "jobs@cbtl.ph");
        createClient("Tim Hortons Philippines", "Pasig City", "Bianca Reyes", "09641234567", "hr@timhortons.ph");
        createClient("Dunkin Philippines", "Bacoor, Cavite", "Andrew Torres", "09651234567", "careers@dunkin.ph");
        createClient("Krispy Kreme Philippines", "Alabang", "Elaine Cruz", "09661234567", "jobs@krispykreme.ph");

        log.info("[DEMO_DATA] Client seed completed clientsCreated={}", DEMO_CLIENT_COUNT);
    }

    private void createClient(String companyName, String address, String contactPerson, String contactNumber, String email) {
        Client client = new Client();

        client.setCompanyName(companyName);
        client.setAddress(address);
        client.setContactPerson(contactPerson);
        client.setContactNumber(contactNumber);
        client.setEmail(email);
        client.setNotes("Seeded demo client");
        client.setActive(true);

        clientRepository.save(client);
    }
}
