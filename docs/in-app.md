<script setup>
    import { useRoute } from 'vitepress'
    import { ref, onMounted } from 'vue'
    import QRCode from 'qrcode'

    import {VPButton} from 'vitepress/theme'


    const route = useRoute()
    const canvas = ref(null)

    const qrcode = ref(null)

    onMounted(async () => {
        qrcode.value = await QRCode.toDataURL(window.location.href)
    })

</script>

# Open settings page

Scan this code to view the linked settings page in Kvaesitso.

<img :src="qrcode">
