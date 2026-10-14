<template>
  <!-- 品牌标：深色圆角方块上的六边形缺口（漏洞 = 完整面上的洞，缺口处 lime 呼吸点） -->
  <span class="brand-mark" aria-hidden="true">
    <svg viewBox="0 0 24 24">
      <path
        d="M16.88 7.31 L18.5 8.25 V15.75 L12 19.5 L5.5 15.75 V8.25 L12 4.5 L14.25 5.63"
        fill="none"
        stroke="rgba(255, 255, 255, .85)"
        stroke-width="1.4"
        stroke-linecap="round"
        stroke-linejoin="round"
      />
      <circle cx="15.6" cy="6.5" r="1.5" fill="#82dd2d" />
    </svg>
  </span>
</template>

<style scoped>
.brand-mark svg { display: block; width: 100%; height: 100%; }
.brand-mark circle { filter: drop-shadow(0 0 3px rgba(130, 221, 45, .55)); }
</style>
